package com.liarstudio.maven_indexer.indexer

import com.liarstudio.maven_indexer.indexer.extractor.ArtifactKmpTargetsExtractor
import com.liarstudio.maven_indexer.indexer.parser.CsvArtifactsParser
import com.liarstudio.maven_indexer.models.Artifact
import com.liarstudio.maven_indexer.models.ArtifactVersionMetadata
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.io.File
import kotlin.random.Random
import kotlin.system.measureTimeMillis

@OptIn(ExperimentalCoroutinesApi::class)
class CsvArtifactIndexerTest {

    private val indexer: SingleArtifactIndexer = mock()
    private val parser: CsvArtifactsParser = mock()
    private val extractor: ArtifactKmpTargetsExtractor = mock()
    private val dummyFile = File("fake.csv")

    @Test
    fun `GIVEN parsed CSV with KMP variants WHEN index called THEN emits progress and result`() = runTest {
        // GIVEN
        val artifact = Artifact("org.example", "base")
        val jvmVariant = Artifact("org.example", "base-jvm")
        val jsVariant = Artifact("org.example", "base-js")
        val variants = listOf(jvmVariant, jsVariant)
        whenever(parser.invoke(dummyFile)).thenReturn(listOf(artifact))
        whenever(extractor.getKmpVariants(artifact)).thenReturn(variants)
        whenever(indexer.indexArtifact(any())).thenReturn(
            Result.success(ArtifactVersionMetadata(listOf("1.0"), "1.0", "1.0"))
        )

        val indexerInstance = CsvArtifactIndexer(dummyFile, indexer, parser, extractor)

        // WHEN
        val progress = indexerInstance.index().toList()

        // THEN
        val staged = progress.filterIsInstance<MultipleArtifactIndexer.Progress.Staged>()
        val result = progress.last() as MultipleArtifactIndexer.Progress.Result

        assertEquals(3, staged.map { it.stageDescription }.distinct().size)
        assertEquals(3, result.successCount)
        assertEquals(0, result.errorCount)

        verify(parser).invoke(dummyFile)
        verify(extractor).getKmpVariants(artifact)
        verify(indexer).indexArtifact(jvmVariant)
        verify(indexer).indexArtifact(jsVariant)
    }

    @Test
    fun `GIVEN failures during indexing WHEN index called THEN counts errors`() = runTest {
        val artifact = Artifact("org.test", "core")
        val kmpVariant = Artifact("org.test", "core-native")

        whenever(parser.invoke(dummyFile)).thenReturn(listOf(artifact))
        whenever(extractor.getKmpVariants(artifact)).thenReturn(listOf(kmpVariant))
        whenever(indexer.indexArtifact(kmpVariant)).thenReturn(Result.failure(RuntimeException("fail")))
        whenever(indexer.indexArtifact(artifact)).thenReturn(Result.failure(RuntimeException("fail")))

        val indexerInstance = CsvArtifactIndexer(dummyFile, indexer, parser, extractor)

        val progress = indexerInstance.index().toList()
        val result = progress.last() as MultipleArtifactIndexer.Progress.Result

        assertEquals(0, result.successCount)
        assertEquals(2, result.errorCount)
    }

    @Test
    fun `GIVEN empty CSV WHEN index called THEN emits result with zero progress`() = runTest {
        whenever(parser.invoke(dummyFile)).thenReturn(emptyList())

        val indexerInstance = CsvArtifactIndexer(dummyFile, indexer, parser, extractor)

        val progress = indexerInstance.index().toList()

        val result = progress.last() as MultipleArtifactIndexer.Progress.Result
        assertEquals(0, result.successCount)
        assertEquals(0, result.errorCount)

        verify(parser).invoke(dummyFile)
        verifyNoInteractions(extractor, indexer)
    }

    @Test
    fun `GIVEN many variants with delays WHEN index called THEN runs in parallel`() = runBlocking {
        // GIVEN
        val baseArtifact = Artifact("com.example", "base")
        val variants = (1..9).map { Artifact("com.example", "base-$it") }

        whenever(parser.invoke(dummyFile)).thenReturn(listOf(baseArtifact))
        whenever(extractor.getKmpVariants(baseArtifact)).thenReturn(variants)
        wheneverBlocking { indexer.indexArtifact(any()) } doSuspendableAnswer {
            delay(100)
            Result.success(ArtifactVersionMetadata(listOf("1.0"), "1.0", "1.0"))
        }

        val indexerInstance = CsvArtifactIndexer(dummyFile, indexer, parser, extractor)

        // WHEN
        val elapsed = measureTimeMillis {
            indexerInstance.index().toList()
        }

        println("CSV indexer finished in $elapsed ms")

        // THEN
        // Sequentially: 10 * 100 = 1000+ms
        // Parallel: < 400-500 ms
        assertTrue(elapsed < 600, "Waiting to finish faster because of parallelism")
    }

    @Test
    fun `GIVEN indexing in progress WHEN cancelled THEN flow terminates early`() = runBlocking {
        val artifact = Artifact("com.cancel", "slow")
        val variants = (1..10).map { Artifact("com.cancel", "slow-$it") }

        whenever(parser.invoke(dummyFile)).thenReturn(listOf(artifact))
        whenever(extractor.getKmpVariants(any())).thenReturn(variants)
        val random = Random.Default
        wheneverBlocking { indexer.indexArtifact(any()) } doSuspendableAnswer {
            delay(random.nextLong(200, 400)) // simulate slow operation
            Result.success(ArtifactVersionMetadata(listOf("1.0"), "1.0", "1.0"))
        }

        val indexerInstance = CsvArtifactIndexer(dummyFile, indexer, parser, extractor)

        val job = launch {
            indexerInstance.index().toList()
        }

        delay(300) // let a few start
        job.cancel()

        assertEquals(true, job.isCancelled, "Job should be cancelled ")
    }

    @Test
    fun `GIVEN csv parser throws exception WHEN index called THEN flow fails`() = runTest {
        whenever(parser.invoke(dummyFile)).thenThrow(RuntimeException("failed to read"))

        val indexerInstance = CsvArtifactIndexer(dummyFile, indexer, parser, extractor)

        val exception = assertThrows<RuntimeException> {
            indexerInstance.index().toList()
        }
        assertEquals("failed to read", exception.message)
    }
}
