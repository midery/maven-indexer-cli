package com.liarstudio.maven_indexer.indexer.parser

import com.liarstudio.maven_indexer.models.Artifact
import com.opencsv.CSVReaderHeaderAware
import java.io.File
import java.io.FileReader

/**
 * Parses artifacts CSV with a strict format:
 *
 * * 2 columns: `namespace`, `name`
 */
class CsvArtifactsParser {

    operator fun invoke(csvFile: File): List<Artifact> {
        val artifacts = mutableListOf<Artifact>()
        val reader = CSVReaderHeaderAware(FileReader(csvFile.absolutePath))
        var row: Map<String, String>? = reader.readMap()
        while (row != null) {
            val group = row["namespace"]
            val artifactId = row["name"]
            if (!group.isNullOrBlank() && !artifactId.isNullOrBlank()) {
                val artifact = Artifact(group, artifactId)
                artifacts.add(artifact)
            } else {
                throw IllegalStateException("Invalid file format: $csvFile. It should have headers named  'namespace' and 'name'.")
            }
            row = reader.readMap()
        }

        return artifacts
    }
}