package com.liarstudio.maven_indexer.cli.processor

import com.liarstudio.maven_indexer.data.storage.ArtifactStorage
import com.liarstudio.maven_indexer.indexer.parser.comparator.VersionComparator
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
            val latest = versionsMeta.latestVersion
            val release = versionsMeta.releaseVersion
            versionsMeta.versions
                .sortedWith(VersionComparator())
                .reversed()
                .forEach { version ->
                    val versionSuffix = when {
                        version == latest && version == release -> "(latest, release)"
                        version == latest -> "(latest)"
                        version == release -> "(release)"
                        else -> ""
                    }
                    println("* $version $versionSuffix")
                }
        }
    }
}