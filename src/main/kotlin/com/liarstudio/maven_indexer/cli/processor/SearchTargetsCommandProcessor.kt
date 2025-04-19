package com.liarstudio.maven_indexer.cli.processor

import com.liarstudio.maven_indexer.data.storage.ArtifactStorage
import com.liarstudio.maven_indexer.indexer.extractor.ArtifactKmpTargetsExtractor.Companion.isKmpVariationOf
import com.liarstudio.maven_indexer.models.Artifact

class SearchTargetsCommandProcessor {


    operator fun invoke(artifact: Artifact, artifactStorage: ArtifactStorage) {
        val targets = artifactStorage.getArtifactTargets(artifact = artifact)
            .filter { it.artifactId.isKmpVariationOf(artifact) }
        if (targets.isEmpty()) {
            println("No Kotlin Multiplatform Targets found for '$artifact'")
            return
        } else {
            println("All Kotlin Multiplatform Targets for '$artifact': ")
            targets.sorted().forEach { println("* $it") }
        }
    }

}