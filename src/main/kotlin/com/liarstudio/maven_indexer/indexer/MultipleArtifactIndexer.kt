package com.liarstudio.maven_indexer.indexer

import kotlinx.coroutines.flow.Flow

interface MultipleArtifactIndexer {

    suspend fun index(): Flow<Progress>

    class Progress(val total: Int?, val current: Int) {
        companion object {
            fun withoutTotal(current: Int) = Progress(null, current)
        }
    }
}