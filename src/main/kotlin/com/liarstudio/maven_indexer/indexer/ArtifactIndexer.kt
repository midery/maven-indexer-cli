package com.liarstudio.maven_indexer.indexer

import com.liarstudio.maven_indexer.indexer.data.ArtifactRepository
import com.liarstudio.maven_indexer.models.Artifact
import com.liarstudio.maven_indexer.models.VersionMetadata
import com.vdurmont.semver4j.Semver
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.byteInputStream

class ArtifactIndexer(val artifactRepository: ArtifactRepository) {

    suspend fun indexArtifact(artifact: Artifact) {
        val groupPath = artifact.groupId.replace('.', '/')
        val artId = artifact.artifactId
        val metadataUrl = "https://repo1.maven.org/maven2/$groupPath/$artId/maven-metadata.xml"
        val xml = runCatching { URL(metadataUrl).readText() }.getOrNull()
        if (xml == null) {
            println("❌ Failed to fetch metadata for $artifact")
            return
        }

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


        artifactRepository.saveArtifact(
            artifact = artifact,
            versionsMetadata = VersionMetadata(
                latestVersion = latestVersion,
                releaseVersion = releaseVersion,
                versions = versions,
            )
        )
    }


    private fun findLatestVersion(latestFromMetadata: String?, allVersions: List<String>): String? {
        return latestFromMetadata ?: return allVersions.maxBy { Semver(it) }
    }
}