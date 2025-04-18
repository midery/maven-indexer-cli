package com.liarstudio.maven_indexer.indexer

import com.liarstudio.maven_indexer.indexer.MultipleArtifactIndexer.Progress
import com.liarstudio.maven_indexer.parser.CsvArtifactsParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class CsvArtifactIndexer(
    private val csvFile: File,
    private val indexer: SingleArtifactIndexer,
    private val csvParser: CsvArtifactsParser,
) : MultipleArtifactIndexer {

    private val mutex = Mutex()

    override suspend fun index(): Flow<Progress> = channelFlow {
        channel.send(Progress(null, 0))

        val artifacts = csvParser.parse(csvFile)
        val artifactsSize = artifacts.size
        val progressStep = artifactsSize / 100

        channel.send(Progress(artifacts.size, 0))
        var processedArtifactsCount = 0
        var lastSentProgress = 0

        artifacts.map {
            async {
                val indexResult = indexer.indexArtifact(it)
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