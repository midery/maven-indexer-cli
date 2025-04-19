package com.liarstudio.maven_indexer.indexer

import com.liarstudio.maven_indexer.data.storage.ArtifactStorage
import com.liarstudio.maven_indexer.indexer.MultipleArtifactIndexer.Progress
import io.ktor.network.tls.NoPrivateKeyException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

class VersionOnlyArtifactIndexer(
    private val artifactStorage: ArtifactStorage,
    private val artifactIndexer: SingleArtifactIndexer,
    private val chunkSize: Int = CHUNK_SIZE,
) : MultipleArtifactIndexer {

    private val semaphore = Semaphore(PARALLELISM)

    override suspend fun index(): Flow<Progress> = channelFlow {
        val artifactsCount = artifactStorage.getArtifactsCount()
        channel.send(Progress.Simple(artifactsCount))
        val errorsCount = AtomicInteger(0)
        val progressCount = AtomicInteger(0)

        val chunkSteps = artifactsCount / chunkSize

        for (i in 0..chunkSteps) {
            val limit = chunkSize
            val offset = i * limit
            artifactStorage.getArtifacts(limit = limit, offset = offset.toLong()).map {
                async {
                    semaphore.withPermit {
                        artifactIndexer.indexArtifact(it)
                            .onSuccess {
                                val progress = progressCount.incrementAndGet()
                                channel.send(Progress.Simple(progress, artifactsCount))
                            }
                            .onFailure { errorsCount.incrementAndGet() }
                    }
                }
            }
                .awaitAll()
        }

        channel.send(Progress.Result(progressCount.get(), errorsCount.get()))
    }

    private companion object {
        const val CHUNK_SIZE = 10_000
        const val PARALLELISM = 64
    }
}