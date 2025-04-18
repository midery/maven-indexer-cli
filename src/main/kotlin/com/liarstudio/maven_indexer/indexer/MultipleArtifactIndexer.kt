package com.liarstudio.maven_indexer.indexer

import kotlinx.coroutines.flow.Flow

interface MultipleArtifactIndexer {

    suspend fun index(): Flow<Progress>

    sealed class Progress() {

        class Staged(val stageDescription: String, val current: Int, val total: Int?, val stageProgress: Simple) :
            Progress()

        class Simple(val current: Int, val total: Int? = null) : Progress()
        class Result(val successCount: Int, val errorCount: Int) : Progress()
    }
}