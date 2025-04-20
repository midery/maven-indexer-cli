package com.liarstudio.maven_indexer.cli.processor

import com.liarstudio.maven_indexer.data.storage.ArtifactStorage


/**
 * Processor for a CLI command of searching for an artifacts via query.
 */
class SearchArtifactsCommandProcessor {

    /**
     * Searches for an artifacts with given [query], and prints the results.
     *
     * Search results are returned sorted by relevance to a query.
     */
    operator fun invoke(query: String, artifactStorage: ArtifactStorage) {
        artifactStorage.searchArtifacts(query, limit = 50).mapIndexed { i, result ->
            val printingIndex = if (i < 9) "0${i + 1}" else "${i + 1}"
            println("$printingIndex. $result")
        }
    }
}