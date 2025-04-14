package com.liarstudio.maven_indexer.crawler

import com.liarstudio.maven_indexer.crawler.ArtifactCrawler.Progress
import com.liarstudio.maven_indexer.indexer.ArtifactIndexer
import com.liarstudio.maven_indexer.models.Artifact
import com.opencsv.CSVReaderHeaderAware
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileReader

/**
 * TODO: Error handling and logging
 */
class CsvArtifactCrawler(
    private val csvFile: File,
    private val indexer: ArtifactIndexer
) : ArtifactCrawler {

    private val mutex = Mutex()

    override suspend fun crawlAndIndex(): Flow<Progress> = channelFlow {
        channel.send(Progress(null, 0))
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
        val artifactsSize = artifacts.size
        val progressStep = artifactsSize / 100

        channel.send(Progress(artifacts.size, 0))
        var processedArtifactsCount = 0
        var lastSentProgress = 0

        artifacts.map {
            async {
                indexer.indexArtifact(it)
                mutex.withLock {
                    processedArtifactsCount++
                    if (processedArtifactsCount - lastSentProgress > progressStep) {
                        lastSentProgress = processedArtifactsCount
                        channel.send(Progress(artifactsSize, processedArtifactsCount))
                    }
                }
            }
        }
            .awaitAll()
        channel.send(Progress(artifacts.size, artifacts.size))
    }
}