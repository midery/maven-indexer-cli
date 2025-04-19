package com.liarstudio.maven_indexer.cli.processor

import com.liarstudio.maven_indexer.indexer.SingleArtifactIndexer
import com.liarstudio.maven_indexer.models.Artifact

class IndexSingleArtifactCommandProcessor {

    suspend operator fun invoke(artifact: Artifact, singleArtifactIndexer: SingleArtifactIndexer) {
        singleArtifactIndexer.indexArtifact(artifact)
    }
}