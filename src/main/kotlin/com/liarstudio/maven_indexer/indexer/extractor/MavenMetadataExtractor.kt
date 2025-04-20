package com.liarstudio.maven_indexer.indexer.extractor

import com.liarstudio.maven_indexer.MAVEN_CENTRAL_REPO_URL
import com.liarstudio.maven_indexer.data.network.NetworkClient
import com.liarstudio.maven_indexer.models.Artifact
import com.liarstudio.maven_indexer.models.ArtifactVersionMetadata
import com.liarstudio.maven_indexer.indexer.parser.XmlMetadataParser
import com.liarstudio.maven_indexer.urlPath

class MavenMetadataExtractor(
    private val networkClient: NetworkClient,
    private val xmlParser: XmlMetadataParser,
    private val host: String = MAVEN_CENTRAL_REPO_URL,
) {

    /**
     * Extract maven metadata for the [artifact]
     *
     * Algorithm: downloads `maven-metadata.xml` file for an artifact, and parses XML with xmlParser.
     */
    suspend operator fun invoke(artifact: Artifact): ArtifactVersionMetadata {
        val metadataUrl = "$host${artifact.urlPath}/$MAVEN_METADATA_FILE"
        val xml = networkClient.getBody(metadataUrl)
        return xmlParser.parse(xml)
    }

    companion object {
        const val MAVEN_METADATA_FILE = "maven-metadata.xml"
    }
}