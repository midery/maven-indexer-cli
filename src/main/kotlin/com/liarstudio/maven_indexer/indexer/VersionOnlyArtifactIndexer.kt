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

/**
 * This indexer acts as an optimization of [FullMavenArtifactIndexer].
 *
 * It does not scan all the Maven Central to do an artifact search, it just goes through already saved artifacts
 * one-by-one and only updates versions metadata from them.
 * This will be x2-x3 times faster then using [FullMavenArtifactIndexer].
 *
 *  TODO: Better optimization: do not save metadata if maven.lastUpdated == local.lastUpdated
 */
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

        // In order to proceed faster and not waste RAM, extracts artifacts with chunks.
        // All the chunks are handled sequentially, but the work inside the chunk is done in parallel.
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
        const val PARALLELISM = 256
    }
}