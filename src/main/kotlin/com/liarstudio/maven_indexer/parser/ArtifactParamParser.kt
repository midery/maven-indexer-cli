package com.liarstudio.maven_indexer.parser

import com.liarstudio.maven_indexer.models.Artifact


fun parseArtifact(param: String): Artifact {
    val artifactPaths = param.split(":")
    if (artifactPaths.size < 2) {
        throw IllegalStateException("Artifact should be specified in a format: foo.bar:xyz or foo.bar.xyz:1.2.3")
    }
    val (groupId, artifactId) = artifactPaths
    return Artifact(artifactId = artifactId, groupId = groupId)
}

val Artifact.groupIdUrlPath get() = groupId.replace('.', '/')
val Artifact.urlPath get() = "$groupIdUrlPath/$artifactId"
