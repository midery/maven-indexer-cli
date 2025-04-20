package com.liarstudio.maven_indexer.cli.processor

import com.liarstudio.maven_indexer.cli.printer.ProgressRenderer
import com.liarstudio.maven_indexer.indexer.MultipleArtifactIndexer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transform
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Processor for a CLI command of indexing multiple artifacts.
 */
class IndexArtifactsCommandProcessor {

    /**
     * Processes multiple artifacts.
     *
     * Prints an output from an [indexer] with 1 second intervals.
     *
     * Runs indexing in IO scheduler, as it is a disk/network sensitive operation.
     *
     * @param indexer runs indexing and sends progress updates
     * @param progressRenderer renders progress in a human-readable format.
     */
    suspend operator fun invoke(indexer: MultipleArtifactIndexer, progressRenderer: ProgressRenderer) {
        println("Start artifacts indexing...")
        indexer.index()
            .flowOn(Dispatchers.IO)
            .throttleLatest(1.seconds).collect { progress ->
                progressRenderer.render(progress)
            }
    }

    private fun <T> Flow<T>.throttleLatest(duration: Duration): Flow<T> = this.conflate().transform {
        emit(it)
        delay(duration)
    }
}