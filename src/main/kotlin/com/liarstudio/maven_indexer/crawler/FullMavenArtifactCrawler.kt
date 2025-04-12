package com.liarstudio.maven_indexer.crawler

import com.liarstudio.maven_indexer.indexer.ArtifactIndexer
import com.liarstudio.maven_indexer.models.Artifact
import org.jsoup.Jsoup

/**
 * TODO: Test this logic
 * TODO: Parallel IO execution
 * TODO: Error handling
 */
class FullMavenArtifactCrawler(private val startUrl: String = MAVEN_CENTRAL_REPO_URL) : ArtifactCrawler {

    override suspend fun crawlAndIndex(indexer: ArtifactIndexer) {

        val queue = ArrayDeque<String>()
        queue.add(startUrl)

        while (queue.isNotEmpty()) {
            val url = queue.removeFirst()

            val html = runCatching { Jsoup.connect(url).get() }.getOrNull() ?: continue
            val links = html.select("a[href]")

            for (link in links) {
                val href = link.attr("href")
                if (href == "../") continue

                val fullUrl = url + href
                if (href.endsWith("/")) {
                    queue.add(fullUrl)
                } else if (href.endsWith("maven-metadata.xml")) {
                    val path = url.removePrefix(startUrl).trim('/')
                    val segments = path.split('/')

                    if (segments.size >= 2) {
                        val artifact = Artifact(
                            artifactId = segments.last(),
                            groupId = segments.dropLast(1).joinToString(".")
                        )

                        println("📦 Found artifact: $artifact")
                        indexer.indexArtifact(artifact)
                    }
                }
            }
        }
    }

    companion object {
        const val MAVEN_CENTRAL_REPO_URL = "https://repo1.maven.org/maven2/"
    }
}