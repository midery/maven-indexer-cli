package com.liarstudio.maven_indexer.crawler

import com.liarstudio.maven_indexer.indexer.ArtifactIndexer

interface ArtifactCrawler {

    suspend fun crawlAndIndex(indexer: ArtifactIndexer)
}