package com.liarstudio.maven_indexer.cli.parser

import com.liarstudio.maven_indexer.models.Artifact


/**
 * Parses artifact from a CLI parameter.
 *
 * Throws illegalStateException if it is specified in a wrong format.
 */
class ArtifactParamParser() {

    operator fun invoke(param: String): Artifact {
        val artifactPaths = param.split(":")
        if (artifactPaths.size < 2) {
            throw IllegalStateException("Artifact should be specified in a format: foo.bar:xyz or foo.bar.xyz:1.2.3")
        }
        val (groupId, artifactId) = artifactPaths
        return Artifact(artifactId = artifactId, groupId = groupId)
    }
}