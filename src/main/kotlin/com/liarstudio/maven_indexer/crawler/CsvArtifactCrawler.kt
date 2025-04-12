package com.liarstudio.maven_indexer.crawler

import com.liarstudio.maven_indexer.indexer.ArtifactIndexer
import com.liarstudio.maven_indexer.models.Artifact
import com.opencsv.CSVReaderHeaderAware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader

/**
 * TODO: Error handling and logging
 */
class CsvArtifactCrawler(private val csvFile: File) : ArtifactCrawler {
    override suspend fun crawlAndIndex(indexer: ArtifactIndexer) = withContext(Dispatchers.IO) {
        val reader = CSVReaderHeaderAware(FileReader(csvFile.absolutePath))
        val artifacts = mutableListOf<Artifact>()
        var row: Map<String, String>? = reader.readMap()
        while (row != null) {
            val group = row["namespace"]
            val artifactId = row["name"]
            if (!group.isNullOrBlank() && !artifactId.isNullOrBlank()) {
                val artifact = Artifact(group, artifactId)
                artifacts.add(artifact)
            }
            row = reader.readMap()
        }

        artifacts.map {
            async {
                indexer.indexArtifact(it)
            }
        }
            .awaitAll()



        println("✅ Done indexing all artifacts from CSV")
    }
}