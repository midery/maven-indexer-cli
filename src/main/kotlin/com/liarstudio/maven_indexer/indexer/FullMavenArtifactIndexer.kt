package com.liarstudio.maven_indexer.indexer

import com.liarstudio.maven_indexer.MAVEN_CENTRAL_REPO_URL
import com.liarstudio.maven_indexer.indexer.MultipleArtifactIndexer.Progress
import com.liarstudio.maven_indexer.models.Artifact
import com.liarstudio.maven_indexer.indexer.extractor.MavenMetadataExtractor.Companion.MAVEN_METADATA_FILE
import com.liarstudio.maven_indexer.indexer.extractor.HtmlPageLinkExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Indexes full maven repository from [host].
 *
 * **Algorithm:** goes through each link one by one and check if the link points on artifact's metadata file.
 * If yes - this is a terminal point, we can extract all the artifact information from the metadata and save it.
 *
 * We use `BFS` algorithm to crawl through the pages, and use [Channel] with unlimited capacity that acts as a queue and
 * helps us to process in parallel. Number of parallel requests is limited by [PARALLELISM] field.
 *
 * In order to finish parallel execution correctly, we keep track of active tasks and state of the [Channel] in another
 * job called [waitForCompletion]. It periodically checks if active tasks and channel are empty,
 * and if this condition is met - finishes it.
 *
 * TODO:
 *  * Improve performance: profile the current setup and think about weak poinrs (better parallelization, batch insert, etc)
 *  * Better error handling
 *  * Save results/errors in a text file, so it can be investigated after the execution.
 *
 *  @param host base maven host
 *  @param additionalPath additional path to shorten the search.
 *      For example, you can specify a specific group/artifact to crawl only inside it.
 *  @param indexer indexer for a single artifact to parse metadata and save it locally.
 *  @param htmlPageLinkExtractor - class used to extract links from html pages
 */
class FullMavenArtifactIndexer(
    private val host: String = MAVEN_CENTRAL_REPO_URL,
    private val additionalPath: String = "",
    private val indexer: SingleArtifactIndexer,
    private val htmlPageLinkExtractor: HtmlPageLinkExtractor,
) : MultipleArtifactIndexer {

    private val logger = LoggerFactory.getLogger(javaClass.simpleName)

    override suspend fun index(): Flow<Progress> = channelFlow {
        val urlHandleChannel = Channel<String>(capacity = UNLIMITED) // Acts as an asynchronous queue in our BFS search
        val progressChannel = channel // Used to send progress to a client
        val visited = ConcurrentHashMap.newKeySet<String>() // Used to filter already visited pages
        val activeTasks = AtomicInteger(0) // Used to track active tasks and to finish the execution
        val progressArtifactCount = AtomicInteger(0)
        val errorsCount = AtomicInteger(0)

        val startUrl = host + additionalPath
        urlHandleChannel.send(startUrl)
        progressChannel.send(Progress.Simple(current = 0))

        val waiterJob = launch { waitForCompletion(activeTasks, urlHandleChannel, visited) }

        val indexingJobs = List(PARALLELISM) {
            launch(Dispatchers.IO) {
                for (url in urlHandleChannel) {
                    try {
                        if (!visited.add(url)) continue
                        processUrl(
                            url = url,
                            activeTasks = activeTasks,
                            progressArtifactCount = progressArtifactCount,
                            errorsCount = errorsCount,
                            progressChannel = progressChannel,
                            urlHandleChannel = urlHandleChannel
                        )
                    } finally {
                        activeTasks.decrementAndGet()
                        logger.debug("Finished processing $url. Active tasks remaining: ${activeTasks.get()}")
                    }
                }
            }
        }

        indexingJobs.joinAll()
        waiterJob.join()
        progressChannel.send(Progress.Result(progressArtifactCount.get(), errorsCount.get()))
    }

    private suspend fun CoroutineScope.processUrl(
        url: String,
        activeTasks: AtomicInteger,
        progressArtifactCount: AtomicInteger,
        errorsCount: AtomicInteger,
        progressChannel: SendChannel<Progress>,
        urlHandleChannel: SendChannel<String>,
    ) {
        logger.debug("Processing: $url")
        activeTasks.incrementAndGet()
        val links = runCatching {
            htmlPageLinkExtractor.invoke(url)
        }
            .getOrElse {
                logger.info("⚠️ Error loading $url: ${it.message}")
                errorsCount.incrementAndGet()
                return
            }

        logger.debug("Found links: ${links.size}")

        val artifactMetadata = links.find { link -> link.endsWith(MAVEN_METADATA_FILE) }
        if (artifactMetadata != null) {
            val artifact = processArtifactMetadata(
                url = url,
                progressArtifactCount = progressArtifactCount,
                errorsCount = errorsCount,
                progressChannel = progressChannel
            )
            logger.debug("Artifact processing completed: $artifact")
        } else {
            for (link in links) {
                logger.debug("Process link: $link")
                val fullUrl = url + link
                if (link.endsWith("/")) {
                    logger.debug("Adding link to the queue: $link")
                    urlHandleChannel.trySend(fullUrl)
                }
            }
        }
    }

    private suspend fun processArtifactMetadata(
        url: String,
        progressArtifactCount: AtomicInteger,
        errorsCount: AtomicInteger,
        progressChannel: SendChannel<Progress>
    ): Artifact? {
        val path = url.removePrefix(host).trim('/')
        val segments = path.split('/')

        logger.debug("Processing $path. $segments")
        if (segments.size >= 2) {
            val artifact = Artifact(
                artifactId = segments.last(),
                groupId = segments.dropLast(1).joinToString(".")
            )

            logger.debug("📦 Found artifact: $artifact")
            indexer.indexArtifact(artifact)
                .fold(
                    onSuccess = { progressArtifactCount.incrementAndGet() },
                    onFailure = { errorsCount.incrementAndGet() }
                )
            progressChannel.trySend(Progress.Simple(current = progressArtifactCount.get()))
            return artifact
        }
        return null
    }

    /**
     * Waits for a completion of a main job and closes [urlHandleChannel] once there's no active tasks
     * and the channel is empty.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun waitForCompletion(
        activeTasks: AtomicInteger,
        urlHandleChannel: Channel<String>,
        visited: Set<String>,
    ) {
        while (true) {
            delay(1000)
            if (activeTasks.get() == 0 && urlHandleChannel.isEmpty) {
                urlHandleChannel.close()
                logger.info("✅ Done processing tasks for $host!")
                break
            } else {
                logger.info("🔄 ActiveTasks: ${activeTasks.get()}, visited: ${visited.size}")
            }
        }

    }

    companion object {
        const val PARALLELISM = 256
    }
}