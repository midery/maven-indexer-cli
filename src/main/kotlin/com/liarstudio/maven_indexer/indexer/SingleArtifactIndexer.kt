package com.liarstudio.maven_indexer.indexer

import com.liarstudio.maven_indexer.data.storage.ArtifactStorage
import com.liarstudio.maven_indexer.models.Artifact
import com.liarstudio.maven_indexer.models.ArtifactVersionMetadata
import com.liarstudio.maven_indexer.indexer.extractor.MavenMetadataExtractor

class SingleArtifactIndexer(
    private val mavenMetadataExtractor: MavenMetadataExtractor,
    private val artifactStorage: ArtifactStorage,
) {

    suspend fun indexArtifact(artifact: Artifact): Result<ArtifactVersionMetadata> = runCatching {
        val versionMeta = mavenMetadataExtractor(artifact)

        artifactStorage.saveArtifact(
            artifact = artifact,
            versionsMetadata = versionMeta
        )

        versionMeta
    }
}