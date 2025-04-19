package com.liarstudio.maven_indexer.indexer

import com.liarstudio.maven_indexer.data.storage.ArtifactStorage
import com.liarstudio.maven_indexer.models.Artifact
import com.liarstudio.maven_indexer.models.ArtifactVersionMetadata
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

@OptIn(ExperimentalCoroutinesApi::class)
class VersionOnlyArtifactIndexerTest {

    private val artifactStorage: ArtifactStorage = mock()
    private val artifactIndexer: SingleArtifactIndexer = mock()
    private val chunkSize = 50

    private val indexer = VersionOnlyArtifactIndexer(artifactStorage, artifactIndexer, chunkSize)

    @Test
    fun `GIVEN 5 artifacts WHEN index called THEN emits progress and result`() = runTest {
        // GIVEN
        val artifacts = (1..5).map { Artifact("com.test", "lib$it") }
        whenever(artifactStorage.getArtifactsCount()).thenReturn(5)
        whenever(artifactStorage.getArtifacts(any(), any())).thenReturn(artifacts)
        whenever(artifactIndexer.indexArtifact(any())).thenReturn(
            Result.success(
                ArtifactVersionMetadata(listOf("1.0"), "1.0", "1.0")
            )
        )

        // WHEN
        val progress = indexer.index().toList()

        // THEN
        assertTrue(progress.first() is MultipleArtifactIndexer.Progress.Simple) // initial size
        // 5 + 1, as we have 5 values, and start from 0
        assertEquals(5 + 1, progress.count { it is MultipleArtifactIndexer.Progress.Simple })
        val result = progress.last()
        assertTrue(result is MultipleArtifactIndexer.Progress.Result)
        result as MultipleArtifactIndexer.Progress.Result
        assertEquals(5, result.successCount)
        assertEquals(0, result.errorCount)

        verify(artifactStorage).getArtifactsCount()
        verify(artifactStorage).getArtifacts(eq(chunkSize), eq(0L))
        verify(artifactIndexer, times(5)).indexArtifact(any())
    }

    @Test
    fun `GIVEN errors during indexing WHEN index called THEN reports correct error count`() = runTest {
        val artifacts = (1..3).map { Artifact("org.test", "fail$it") }
        whenever(artifactStorage.getArtifactsCount()).thenReturn(3)
        whenever(artifactStorage.getArtifacts(any(), any())).thenReturn(artifacts)

        whenever(artifactIndexer.indexArtifact(any()))
            .thenReturn(Result.failure(RuntimeException("Oops")))

        val progress = indexer.index().toList()

        val result = progress.last() as MultipleArtifactIndexer.Progress.Result
        assertEquals(0, result.successCount)
        assertEquals(3, result.errorCount)
    }


    @Test
    fun `GIVEN delayed indexers WHEN index called THEN processes in parallel`() = runBlocking {
        val artifactCount = 10
        val artifacts = (1..artifactCount).map { Artifact("com.example", "lib$it") }

        whenever(artifactStorage.getArtifactsCount()).thenReturn(artifactCount)
        whenever(artifactStorage.getArtifacts(any(), any())).thenReturn(artifacts)

        wheneverBlocking { artifactIndexer.indexArtifact(any()) } doSuspendableAnswer {
            delay(100)
            Result.success(ArtifactVersionMetadata(listOf("1.0"), "1.0", "1.0"))
        }

        val elapsed = measureTimeMillis {
            indexer.index().toList()
        }

        println("Completed in $elapsed ms")

        // One coroutine would need ~1000ms
        assertTrue(elapsed < 600, "Waiting to finish faster because of parallelism")
    }


    @Test
    fun `GIVEN chunkSize smaller than total WHEN index called THEN storage is queried multiple times`() = runTest {
        // GIVEN
        val total = 210
        val artifacts = (1..chunkSize).map { Artifact("org.example", "lib$it") }

        whenever(artifactStorage.getArtifactsCount()).thenReturn(total)
        whenever(artifactStorage.getArtifacts(any(), any())).thenReturn(artifacts)
        whenever(artifactIndexer.indexArtifact(any())).thenReturn(
            Result.success(ArtifactVersionMetadata(listOf("1.0"), "1.0", "1.0"))
        )

        val indexer = VersionOnlyArtifactIndexer(
            artifactStorage = artifactStorage,
            artifactIndexer = artifactIndexer,
            chunkSize = chunkSize
        )

        // WHEN
        indexer.index().toList()

        // THEN
        val expectedCalls = 5 // 50, 100, 150, 200, 210
        verify(artifactStorage, times(expectedCalls)).getArtifacts(eq(chunkSize), any())
    }

    @Test
    fun `GIVEN chunkSize larger than total WHEN index called THEN storage is queried once`() = runTest {
        val total = 5
        val artifacts = (1..total).map { Artifact("org.example", "lib$it") }

        whenever(artifactStorage.getArtifactsCount()).thenReturn(total)
        whenever(artifactStorage.getArtifacts(any(), any())).thenReturn(artifacts)
        whenever(artifactIndexer.indexArtifact(any())).thenReturn(
            Result.success(ArtifactVersionMetadata(listOf("1.0"), "1.0", "1.0"))
        )

        val indexer = VersionOnlyArtifactIndexer(
            artifactStorage = artifactStorage,
            artifactIndexer = artifactIndexer,
            chunkSize = chunkSize
        )

        indexer.index().toList()

        verify(artifactStorage, times(1)).getArtifacts(eq(chunkSize), eq(0L))
    }
}
