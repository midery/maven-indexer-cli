package com.liarstudio.maven_indexer.indexer

import com.liarstudio.maven_indexer.data.storage.ArtifactStorage
import com.liarstudio.maven_indexer.models.Artifact
import com.liarstudio.maven_indexer.models.ArtifactVersionMetadata
import com.liarstudio.maven_indexer.parser.MavenMetadataParser

class SingleArtifactIndexer(
    private val mavenMetadataParser: MavenMetadataParser,
    private val artifactStorage: ArtifactStorage,
) {

    suspend fun indexArtifact(artifact: Artifact): Result<ArtifactVersionMetadata> = runCatching {
        val versionMeta = mavenMetadataParser.parse(artifact)

        artifactStorage.saveArtifact(
            artifact = artifact,
            versionsMetadata = versionMeta
        )

        versionMeta
    }
}