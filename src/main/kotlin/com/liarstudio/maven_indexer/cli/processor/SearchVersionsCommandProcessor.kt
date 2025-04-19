package com.liarstudio.maven_indexer.cli.processor

import com.liarstudio.maven_indexer.data.storage.ArtifactStorage
import com.liarstudio.maven_indexer.models.Artifact

class SearchVersionsCommandProcessor {

    operator fun invoke(
        artifact: Artifact, artifactStorage: ArtifactStorage
    ) {
        val versionsMeta = artifactStorage.getArtifactVersions(artifact = artifact)
        if (versionsMeta.versions.isEmpty()) {
            println("No versions found")
            return
        } else {
            println("All Versions for '$artifact': ")
            versionsMeta.versions.sortedDescending().forEach { version ->
                val versionSuffix = when (version) {
                    versionsMeta.latestVersion -> "(latest)"
                    versionsMeta.releaseVersion -> "(release)"
                    else -> ""
                }
                println("* $version $versionSuffix")
            }
        }
    }
}