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

class IndexArtifactsCommandProcessor {

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