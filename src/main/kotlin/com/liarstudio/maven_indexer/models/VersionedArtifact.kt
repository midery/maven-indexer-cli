package com.liarstudio.maven_indexer.models

data class VersionedArtifact(
    val groupId: String,
    val artifactId: String,
    val version: String,
) {

    override fun toString(): String {
        return "$groupId:$artifactId:$version"
    }
}