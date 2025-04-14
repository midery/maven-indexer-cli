package com.liarstudio.maven_indexer.crawler

import com.liarstudio.maven_indexer.crawler.ArtifactCrawler.Progress
import com.liarstudio.maven_indexer.indexer.ArtifactIndexer
import com.liarstudio.maven_indexer.models.Artifact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * TODO: Test this logic
 * TODO: Parallel IO execution
 * TODO: Error handling
 */
class FullMavenArtifactCrawler(
    private val startUrl: String = MAVEN_CENTRAL_REPO_URL,
    private val indexer: ArtifactIndexer,
) : ArtifactCrawler {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val visited = ConcurrentHashMap.newKeySet<String>()
    private val urlHandleChannel = Channel<String>(capacity = Channel.UNLIMITED)
    private val mutex = Mutex()

    override suspend fun crawlAndIndex(): Flow<Progress> = channelFlow {
        urlHandleChannel.send(startUrl)
        val progressChannel = channel
        val processedArtifactsCount = AtomicInteger()
        var lastSentProgress = 0
        val progressStep = 1000

        repeat(64) {
            launch(Dispatchers.IO) {
                for (url in urlHandleChannel) {
                    if (!visited.add(url)) continue
                    logger.debug("Processing: $url")
                    val html = Jsoup.connect(url).get()
                    logger.debug("Processing HTML...")

                    val links = html.select("a[href]")
                    logger.debug("Found links: ${links.size}")

                    for (link in links) {
                        val href = link.attr("href")
                        logger.debug("Found link: $href")
                        if (href == "../") continue

                        val fullUrl = url + href
                        if (href.endsWith("/")) {
                            logger.debug("Adding link to the queue: $href")
                            urlHandleChannel.send(fullUrl)
                        } else if (href.endsWith("maven-metadata.xml")) {
                            logger.debug("Metadata found! $fullUrl")
                            val path = url.removePrefix(startUrl).trim('/')
                            val segments = path.split('/')

                            if (segments.size >= 2) {
                                val artifact = Artifact(
                                    artifactId = segments.last(),
                                    groupId = segments.dropLast(1).joinToString(".")
                                )

                                logger.info("📦 Found artifact: $artifact")
                                runCatching { indexer.indexArtifact(artifact) }
                                val processedArtifactsCount = processedArtifactsCount.incrementAndGet()
                                mutex.withLock {
                                    if (processedArtifactsCount - lastSentProgress >= progressStep) {
                                        lastSentProgress =  processedArtifactsCount
                                        progressChannel.send(Progress.withoutTotal(current = processedArtifactsCount))
                                    }
                                }

                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val MAVEN_CENTRAL_REPO_URL = "https://repo1.maven.org/maven2/"
    }
}