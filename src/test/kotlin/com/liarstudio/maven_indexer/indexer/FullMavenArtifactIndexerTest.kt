package com.liarstudio.maven_indexer.indexer

import com.liarstudio.maven_indexer.indexer.extractor.HtmlPageLinkExtractor
import com.liarstudio.maven_indexer.models.Artifact
import com.liarstudio.maven_indexer.models.ArtifactVersionMetadata
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.wheneverBlocking
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

class FullMavenArtifactIndexerTest {
    private val htmlPageLinkExtractor: HtmlPageLinkExtractor = mock()
    private val indexer: SingleArtifactIndexer = mock()

    @BeforeEach
    fun setUp() {
//        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn")
    }


    @Test
    fun `GIVEN single url with maven-metadata WHEN indexed THEN emits progress and result`() = runTest {
        val baseUrl = "https://mock.repo/"
        val metadataUrl = "${baseUrl}com/example/lib/"
        val metadataLink = "maven-metadata.xml"
        val artifact = Artifact("com.example", "lib")

        wheneverBlocking { htmlPageLinkExtractor.invoke(baseUrl) }.thenReturn(listOf("com/"))
        wheneverBlocking { htmlPageLinkExtractor.invoke("${baseUrl}com/") }.thenReturn(listOf("example/"))
        wheneverBlocking { htmlPageLinkExtractor.invoke("${baseUrl}com/example/") }.thenReturn(listOf("lib/"))
        wheneverBlocking { htmlPageLinkExtractor.invoke(metadataUrl) }.thenReturn(listOf(metadataLink))
        wheneverBlocking { indexer.indexArtifact(artifact) }.thenReturn(
            Result.success(ArtifactVersionMetadata(listOf("1.0"), "1.0", "1.0"))
        )

        val fullIndexer = FullMavenArtifactIndexer(baseUrl, "", indexer, htmlPageLinkExtractor)

        val progress = fullIndexer.index().toList()

        val result = progress.last() as MultipleArtifactIndexer.Progress.Result
        assertEquals(1, result.successCount)
        assertEquals(0, result.errorCount)
    }

    @Test
    fun `GIVEN error during html extraction WHEN indexed THEN counts as failure`() = runTest {
        val baseUrl = "https://error.repo/"

        wheneverBlocking { htmlPageLinkExtractor.invoke(baseUrl) }.thenThrow(RuntimeException("oops"))

        val fullIndexer = FullMavenArtifactIndexer(baseUrl, "", indexer, htmlPageLinkExtractor)

        val progress = fullIndexer.index().toList()
        val result = progress.last() as MultipleArtifactIndexer.Progress.Result

        assertEquals(0, result.successCount)
        assertEquals(1, result.errorCount)
    }


    @Test
    fun `GIVEN metadata path with too few segments WHEN indexed THEN skipped`() = runTest {
        val baseUrl = "https://weird.repo/"
        val badUrl = "${baseUrl}invalid/"

        wheneverBlocking { htmlPageLinkExtractor.invoke(baseUrl) }.thenReturn(listOf("invalid/"))
        wheneverBlocking { htmlPageLinkExtractor.invoke(badUrl) }.thenReturn(listOf("maven-metadata.xml"))

        val fullIndexer = FullMavenArtifactIndexer(baseUrl, "", indexer, htmlPageLinkExtractor)

        val progress = fullIndexer.index().toList()
        val result = progress.last() as MultipleArtifactIndexer.Progress.Result

        assertEquals(0, result.successCount)
        assertEquals(0, result.errorCount)
    }

    @Test
    fun `GIVEN nested urls without metadata WHEN indexed THEN follows links`() = runTest {
        val baseUrl = "https://deep.repo/"
        wheneverBlocking { htmlPageLinkExtractor.invoke(baseUrl) }.thenReturn(listOf("lib/", "util/"))
        wheneverBlocking { htmlPageLinkExtractor.invoke("${baseUrl}lib/") }.thenReturn(emptyList())
        wheneverBlocking { htmlPageLinkExtractor.invoke("${baseUrl}util/") }.thenReturn(emptyList())

        val fullIndexer = FullMavenArtifactIndexer(baseUrl, "", indexer, htmlPageLinkExtractor)

        val progress = fullIndexer.index().toList()
        val result = progress.last() as MultipleArtifactIndexer.Progress.Result

        assertEquals(0, result.successCount)
        assertEquals(0, result.errorCount)
    }

    // Large test, executed for ~15-30 seconds
    @Test
    fun `GIVEN 10_000 3-level urls WHEN indexed THEN processed correctly in parallel`() = runBlocking {
        val baseUrl = "https://mock.repo/"
        val groupCount = 100
        val artifactCount = 100

        // example: com/example1/lib1/
        val groups = (1..groupCount).map { "group$it" }
        val artifacts = (1..artifactCount).map { "lib$it" }

        // root returns 'com/'
        wheneverBlocking { htmlPageLinkExtractor.invoke(baseUrl) } doSuspendableAnswer { listOf("com/") }

        // com/ returns groupX/
        wheneverBlocking { htmlPageLinkExtractor.invoke("${baseUrl}com/") } doSuspendableAnswer {
            delay(10)
            groups.map { "$it/" }
        }

        // com/groupX/ returns libX/
        groups.forEachIndexed { index, group ->
            wheneverBlocking {
                htmlPageLinkExtractor.invoke("${baseUrl}com/$group/")
            }.doSuspendableAnswer {
                artifacts.map { "$it/" }
            }

            // each com/groupX/libX returns maven-metadata.xml
            artifacts.forEach { artifact ->
                val url = "${baseUrl}com/$group/$artifact/"
                wheneverBlocking { htmlPageLinkExtractor.invoke(url) } doSuspendableAnswer { listOf("maven-metadata.xml") }
            }
        }

        wheneverBlocking { indexer.indexArtifact(any()) } doSuspendableAnswer {
            delay(10)
            Result.success(ArtifactVersionMetadata(listOf("1.0"), "1.0", "1.0"))
        }

        val fullIndexer = FullMavenArtifactIndexer(
            host = baseUrl,
            indexer = indexer,
            htmlPageLinkExtractor = htmlPageLinkExtractor
        )

        val elapsed = measureTimeMillis {
            val progress = fullIndexer.index().toList()
            val result = progress.last() as MultipleArtifactIndexer.Progress.Result

            assertEquals(groupCount * artifactCount, result.successCount)
            assertEquals(0, result.errorCount)
        }

        // THEN
        // Sequentially: 10_000 * 10ms = 100s
        // Parallel: < 10s
        println("✅ BFS-3-level load test finished in $elapsed ms")
        assertTrue(elapsed < 10_000, "Waiting to finish faster because of parallelism")
    }
}
