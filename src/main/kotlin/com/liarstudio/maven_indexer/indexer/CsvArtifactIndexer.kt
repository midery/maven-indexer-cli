package com.liarstudio.maven_indexer.indexer

import com.liarstudio.maven_indexer.indexer.MultipleArtifactIndexer.Progress
import com.liarstudio.maven_indexer.indexer.extractor.ArtifactKmpTargetsExtractor
import com.liarstudio.maven_indexer.indexer.parser.CsvArtifactsParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class CsvArtifactIndexer(
    private val csvFile: File,
    private val indexer: SingleArtifactIndexer,
    private val csvParser: CsvArtifactsParser,
    private val kmpVariantsExtractor: ArtifactKmpTargetsExtractor,
) : MultipleArtifactIndexer {

    private val semaphore = Semaphore(permits = PARALLELISM)

    override suspend fun index(): Flow<Progress> = channelFlow {
        channel.send(readFileStageProgress)
        val csvArtifacts = csvParser.invoke(csvFile)
        var artifactsSize = csvArtifacts.size
        val processedArtifactsCount = AtomicInteger(0)
        val processingErrors = AtomicInteger(0)

        channel.send(extractKmpTargetsStageProgress(0, artifactsSize))
        val kmmArtifactVariants = csvArtifacts
            .mapIndexed { i, artifact ->
                async {
                    semaphore.withPermit {
                        val kmpVariants = kmpVariantsExtractor.getKmpVariants(artifact)
                        channel.send(
                            extractKmpTargetsStageProgress(
                                processedArtifactsCount.incrementAndGet(),
                                artifactsSize
                            )
                        )
                        kmpVariants + artifact
                    }
                }
            }
            .awaitAll()
            .flatten()
            .toSet()

        processedArtifactsCount.set(0)
        artifactsSize = kmmArtifactVariants.size

        kmmArtifactVariants.map {
            async {
                semaphore.withPermit {
                    indexer.indexArtifact(it)
                        .fold(
                            onSuccess = { processedArtifactsCount.incrementAndGet() },
                            onFailure = { processingErrors.incrementAndGet() }
                        )
                    channel.send(fetchVersionsStageProgress(processedArtifactsCount.get(), artifactsSize))
                }
            }
        }
            .awaitAll()

        channel.send(
            Progress.Result(
                successCount = processedArtifactsCount.get(),
                errorCount = processingErrors.get()
            )
        )
    }

    companion object {
        private const val PARALLELISM = 64

        private val readFileStageProgress =
            Progress.Staged("Reading artifacts from CSV", 1, 3, Progress.Simple(0, 1))

        private fun extractKmpTargetsStageProgress(currentArtifact: Int, totalArtifacts: Int) =
            Progress.Staged("Extracting KMP variants", 2, 3, Progress.Simple(currentArtifact, totalArtifacts))

        private fun fetchVersionsStageProgress(currentArtifact: Int, totalArtifacts: Int) =
            Progress.Staged("Fetching versions", 3, 3, Progress.Simple(currentArtifact, totalArtifacts))

    }
}