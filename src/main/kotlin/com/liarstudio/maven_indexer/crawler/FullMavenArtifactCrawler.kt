package com.liarstudio.maven_indexer.crawler

import com.liarstudio.maven_indexer.indexer.ArtifactIndexer
import com.liarstudio.maven_indexer.models.Artifact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * TODO: Test this logic
 * TODO: Parallel IO execution
 * TODO: Error handling
 */
class FullMavenArtifactCrawler(
    private val startUrl: String = MAVEN_CENTRAL_REPO_URL,
) : ArtifactCrawler {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val visited = ConcurrentHashMap.newKeySet<String>()
    private val channel = Channel<String>(capacity = Channel.UNLIMITED)


    override suspend fun crawlAndIndex(indexer: ArtifactIndexer) = withContext(Dispatchers.IO) {
        channel.send(startUrl)

        repeat(64) {
            launch {
                for (url in channel) {
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
                            channel.send(fullUrl)
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