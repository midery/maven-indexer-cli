package com.liarstudio.maven_indexer.cli.processor

import com.liarstudio.maven_indexer.indexer.SingleArtifactIndexer
import com.liarstudio.maven_indexer.models.Artifact

/**
 * Processor for a CLI command of indexing multiple artifacts.
 */
class IndexSingleArtifactCommandProcessor {

    suspend operator fun invoke(artifact: Artifact, singleArtifactIndexer: SingleArtifactIndexer) {
        singleArtifactIndexer.indexArtifact(artifact)
            .fold(
                onSuccess = { versionMeta ->
                    println("Artifact successfully indexed! Last release version: ${artifact}:${versionMeta.releaseVersion}")
                },
                onFailure = { error ->
                    println("⚠️ Error indexing $artifact: ${error.message}")
                }
            )
    }
}