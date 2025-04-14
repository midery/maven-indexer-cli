package com.liarstudio.maven_indexer.parser

import com.liarstudio.maven_indexer.data.NetworkClient
import com.liarstudio.maven_indexer.models.VersionMetadata
import javax.xml.parsers.DocumentBuilderFactory

class MavenMetadataParser(private val networkClient: NetworkClient) {

    suspend fun parse(metadataUrl: String): VersionMetadata? {
        try {
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

            return VersionMetadata(versions, latestVersion, releaseVersion)
        } catch (e: Exception) {
            println("❌ Failed to fetch metadata for $metadataUrl")
            return null
        }
    }

    private fun findLatestVersion(latestFromMetadata: String?, allVersions: List<String>): String? {
        return latestFromMetadata ?: return allVersions.maxOrNull()
    }

}