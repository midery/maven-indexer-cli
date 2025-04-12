package com.liarstudio.maven_indexer.indexer

import com.liarstudio.maven_indexer.indexer.data.IndexedArtifactDao
import com.liarstudio.maven_indexer.models.Artifact
import com.liarstudio.maven_indexer.models.IndexedArtifact
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.byteInputStream

class ArtifactIndexer {

    fun indexArtifact(artifact: Artifact): List<IndexedArtifact> {
        val groupPath = artifact.groupId.replace('.', '/')
        val artifactId = artifact.artifactId
        val metadataUrl = "https://repo1.maven.org/maven2/$groupPath/$artifactId/maven-metadata.xml"
        val xml = runCatching { URL(metadataUrl).readText() }.getOrNull()
        if (xml == null) {
            println("❌ Failed to fetch metadata for $artifact")
            return listOf()
        }

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
        val versions = doc.getElementsByTagName("version").let { nodes ->
            (0 until nodes.length).map { nodes.item(it).textContent }
        }

        val artifacts = versions
            .mapNotNull { version ->
                IndexedArtifact(artifact.groupId, artifactId, version)
            }
            .distinct()
        transaction {
            IndexedArtifactDao.batchInsert(artifacts) { artifact ->
                this[IndexedArtifactDao.groupId] = artifact.groupId.take(255)
                this[IndexedArtifactDao.artifactId] = artifact.artifactId.take(255)
                this[IndexedArtifactDao.version] = artifact.version.take(100)
            }

        }

        return artifacts
    }
}