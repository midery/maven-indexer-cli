package com.liarstudio.maven_indexer.crawler

import kotlinx.coroutines.flow.Flow

interface ArtifactCrawler {

    suspend fun crawlAndIndex(): Flow<Progress>

    class Progress(val total: Int?, val current: Int) {
        companion object {
            fun withoutTotal(current: Int) = Progress(null, current)
        }
    }
}