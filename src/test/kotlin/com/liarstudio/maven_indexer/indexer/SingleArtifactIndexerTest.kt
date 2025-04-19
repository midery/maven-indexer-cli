package com.liarstudio.maven_indexer.indexer

import com.liarstudio.maven_indexer.data.storage.ArtifactStorage
import com.liarstudio.maven_indexer.indexer.extractor.MavenMetadataExtractor
import com.liarstudio.maven_indexer.models.Artifact
import com.liarstudio.maven_indexer.models.ArtifactVersionMetadata
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class SingleArtifactIndexerTest {

    private val extractor: MavenMetadataExtractor = mock()
    private val storage: ArtifactStorage = mock()
    private val indexer = SingleArtifactIndexer(extractor, storage)

    @Test
    fun `GIVEN valid metadata WHEN indexArtifact called THEN saves and returns metadata`() = runTest {
        // GIVEN
        val artifact = Artifact("org.example", "lib")
        val metadata = ArtifactVersionMetadata(
            versions = listOf("1.0.0", "1.1.0"),
            latestVersion = "1.1.0",
            releaseVersion = "1.0.0"
        )

        whenever(extractor.invoke(artifact)).thenReturn(metadata)

        // WHEN
        val result = indexer.indexArtifact(artifact)

        // THEN
        assertTrue(result.isSuccess)
        assertEquals(metadata, result.getOrNull())
        verify(extractor).invoke(artifact)
        verify(storage).saveArtifact(artifact, metadata)
    }

    @Test
    fun `GIVEN extractor throws WHEN indexArtifact called THEN returns failure`() = runTest {
        val artifact = Artifact("org.fail", "broken")

        whenever(extractor.invoke(artifact)).thenThrow(RuntimeException("Boom!"))

        val result = indexer.indexArtifact(artifact)

        assertTrue(result.isFailure)
        verify(extractor).invoke(artifact)
        verify(storage, never()).saveArtifact(any(), any())
    }
}
