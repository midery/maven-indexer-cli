package com.liarstudio.maven_indexer.parser

import com.liarstudio.maven_indexer.MAVEN_CENTRAL_REPO_URL
import com.liarstudio.maven_indexer.data.network.NetworkClient
import com.liarstudio.maven_indexer.models.Artifact
import com.liarstudio.maven_indexer.models.ArtifactVersionMetadata
import javax.xml.parsers.DocumentBuilderFactory

class MavenMetadataParser(
    private val networkClient: NetworkClient,
    private val host: String = MAVEN_CENTRAL_REPO_URL,
) {

    suspend fun parse(artifact: Artifact): ArtifactVersionMetadata {
        val metadataUrl = "$host${artifact.urlPath}/maven-metadata.xml"
        val xml = networkClient.getBody(metadataUrl)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
        val documentElement = doc.documentElement
        val versions = documentElement.getElementsByTagName("version").let { nodes ->
            (0 until nodes.length).map { nodes.item(it).textContent }
        }

        val latestVersion =
            findLatestVersion(
                documentElement.getElementsByTagName("latest").item(0)?.textContent,
                versions
            )
        val releaseVersion =
            findLatestVersion(
                documentElement.getElementsByTagName("release").item(0)?.textContent,
                versions
            )

        return ArtifactVersionMetadata(versions, latestVersion, releaseVersion)
    }

    private fun findLatestVersion(latestFromMetadata: String?, allVersions: List<String>): String? {
        return latestFromMetadata ?: return allVersions.maxOrNull()
    }

}