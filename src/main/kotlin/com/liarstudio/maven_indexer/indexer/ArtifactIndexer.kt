package com.liarstudio.maven_indexer.indexer

import com.liarstudio.maven_indexer.indexer.data.ArtifactStorage
import com.liarstudio.maven_indexer.models.Artifact
import com.liarstudio.maven_indexer.parser.MavenMetadataParser

class ArtifactIndexer(
    val mavenMetadataParser: MavenMetadataParser,
    val artifactStorage: ArtifactStorage
) {

    suspend fun indexArtifact(artifact: Artifact) {
        val groupPath = artifact.groupId.replace('.', '/')
        val artId = artifact.artifactId
        val versionMeta =
            mavenMetadataParser.parse("https://repo1.maven.org/maven2/$groupPath/$artId/maven-metadata.xml")

        versionMeta ?: return

        artifactStorage.saveArtifact(
            artifact = artifact,
            versionsMetadata = versionMeta
        )
    }
}