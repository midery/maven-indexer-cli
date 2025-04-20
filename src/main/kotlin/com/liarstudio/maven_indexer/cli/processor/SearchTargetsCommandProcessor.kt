package com.liarstudio.maven_indexer.cli.processor

import com.liarstudio.maven_indexer.data.storage.ArtifactStorage
import com.liarstudio.maven_indexer.indexer.extractor.ArtifactKmpTargetsExtractor.Companion.isKmpVariationOf
import com.liarstudio.maven_indexer.models.Artifact

/**
 * Processor for a CLI command of searching for available Kotlin Multi Platform artifact targets.
 */
class SearchTargetsCommandProcessor {

    /**
     * Searches for available Kotlin Multiplatform Targets for of a single artifact and prints the results.
     *
     * An artifact is considered a KMP target of a single artifact by using a [isKmpVariationOf] function.
     */
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