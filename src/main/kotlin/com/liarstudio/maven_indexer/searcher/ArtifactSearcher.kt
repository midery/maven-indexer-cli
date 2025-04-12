package com.liarstudio.maven_indexer.searcher

import com.liarstudio.maven_indexer.indexer.data.ArtifactRepository
import com.liarstudio.maven_indexer.models.IndexedArtifact

class ArtifactSearcher(private val artifactRepository: ArtifactRepository) {

    // TODO: Search with ranks: ORDER BY CASE -> then FTS25
    suspend fun search(query: String, limit: Int = 50): List<IndexedArtifact> =
        artifactRepository.getArtifacts(query, limit)
}