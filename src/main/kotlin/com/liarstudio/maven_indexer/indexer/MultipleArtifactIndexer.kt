package com.liarstudio.maven_indexer.indexer

import kotlinx.coroutines.flow.Flow

interface MultipleArtifactIndexer {

    suspend fun index(): Flow<Progress>

    sealed class Progress(val current: Int, val total: Int?) {

        class Staged(val stageDescription: String, current: Int, total: Int?, val stageProgress: Simple) :
            Progress(current, total)

        class Simple(current: Int, total: Int? = null) : Progress(current, total)
    }
}