package com.liarstudio.maven_indexer.indexer

import com.liarstudio.maven_indexer.indexer.MultipleArtifactIndexer.Progress
import com.liarstudio.maven_indexer.indexer.kmp.ArtifactKmpVariantsExtractor
import com.liarstudio.maven_indexer.parser.CsvArtifactsParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File

class CsvArtifactIndexer(
    private val csvFile: File,
    private val indexer: SingleArtifactIndexer,
    private val csvParser: CsvArtifactsParser,
    private val kmpVariantsExtractor: ArtifactKmpVariantsExtractor,
) : MultipleArtifactIndexer {

    private val mutex = Mutex()
    private val semaphore = Semaphore(permits = PARALLELISM)

    override suspend fun index(): Flow<Progress> = channelFlow {
        channel.send(Progress(null, 0))

        val csvArtifacts = csvParser.parse(csvFile)
        val kmmArtifactVariants = csvArtifacts
            .map { artifact ->
                async {
                    semaphore.withPermit {
                       kmpVariantsExtractor.getKmpVariants(artifact)
                    }
                }
            }
            .awaitAll()
            .flatten()
        val artifactsSize = kmmArtifactVariants.size

        val progressStep = artifactsSize / 100

        channel.send(Progress(kmmArtifactVariants.size, 0))
        var processedArtifactsCount = 0
        var lastSentProgress = 0

        kmmArtifactVariants.map {
            async {
                semaphore.withPermit {
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
        }
            .awaitAll()
        channel.send(Progress(kmmArtifactVariants.size, kmmArtifactVariants.size))
    }

    companion object {
        private const val PARALLELISM = 64
    }
}