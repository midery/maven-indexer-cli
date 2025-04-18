package com.liarstudio.maven_indexer.parser

import com.liarstudio.maven_indexer.models.Artifact
import com.opencsv.CSVReaderHeaderAware
import java.io.File
import java.io.FileReader

class CsvArtifactsParser {

    fun parse(csvFile: File): List<Artifact> {
        val artifacts = mutableListOf<Artifact>()
        val reader = CSVReaderHeaderAware(FileReader(csvFile.absolutePath))
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

        return artifacts
    }
}