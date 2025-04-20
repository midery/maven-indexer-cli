package com.liarstudio.maven_indexer.indexer.parser

import com.liarstudio.maven_indexer.indexer.parser.comparator.VersionComparator
import com.liarstudio.maven_indexer.models.ArtifactVersionMetadata
import javax.xml.parsers.DocumentBuilderFactory

class XmlMetadataParser(
    val versionComparator: VersionComparator
) {

    fun parse(xml: String): ArtifactVersionMetadata {
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
        println("latestFromMetadata: $latestFromMetadata")
        return latestFromMetadata ?: return allVersions.maxWithOrNull(versionComparator)
    }
}