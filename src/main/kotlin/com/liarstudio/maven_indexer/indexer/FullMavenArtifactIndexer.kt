package com.liarstudio.maven_indexer.indexer

import com.liarstudio.maven_indexer.MAVEN_CENTRAL_REPO_URL
import com.liarstudio.maven_indexer.indexer.MultipleArtifactIndexer.Progress
import com.liarstudio.maven_indexer.models.Artifact
import com.liarstudio.maven_indexer.parser.WebPageLinkUrlParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
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
 * TODO: Test this logic
 * TODO: Error handling
 */
class FullMavenArtifactIndexer(
    private val host: String = MAVEN_CENTRAL_REPO_URL,
    private val additionalPath: String = "",
    private val indexer: SingleArtifactIndexer,
    private val webPageLinkUrlParser: WebPageLinkUrlParser,
) : MultipleArtifactIndexer {

    private val logger = LoggerFactory.getLogger(javaClass.simpleName)

    override suspend fun index(): Flow<Progress> = channelFlow {
        val urlHandleChannel = Channel<String>(capacity = Channel.UNLIMITED)
        val progressChannel = channel
        val visited = ConcurrentHashMap.newKeySet<String>()
        val activeTasks = AtomicInteger(0)
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
            webPageLinkUrlParser.parse(url)
        }
            .getOrElse {
                errorsCount.incrementAndGet()
                return
            }

        logger.debug("Found links: ${links.size}")

        val artifactMetadata = links.find { link -> link.endsWith(suffix = MAVEN_METADATA_FILE) }
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

    private suspend fun CoroutineScope.processArtifactMetadata(
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
        const val MAVEN_METADATA_FILE = "maven-metadata.xml"
    }
}