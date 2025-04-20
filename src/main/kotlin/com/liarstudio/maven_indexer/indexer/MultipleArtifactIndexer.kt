package com.liarstudio.maven_indexer.indexer

import kotlinx.coroutines.flow.Flow

/**
 * Indexer used to crawl and extract meta-information about multiple artifacts.
 */
interface MultipleArtifactIndexer {

    /**
     * Indexes multiple artifacts.
     *
     * As this process can be quite long, this interface is required to send regular [Progress] updates to the consumer,
     * so it can display the loading state and notify the user.
     *
     * This method is designed to consume all the errors of a single artifacts indexing during the processing stage, so
     * it does not affect the indexing as a whole.
     *
     * After process is finished, clients can see the amount of
     * errors / successful indexing in the statistics of [Progress.Result].
     */
    suspend fun index(): Flow<Progress>

    sealed class Progress() {

        class Staged(val stageDescription: String, val current: Int, val total: Int?, val stageProgress: Simple) :
            Progress()

        class Simple(val current: Int, val total: Int? = null) : Progress()
        class Result(val successCount: Int, val errorCount: Int) : Progress()
    }
}