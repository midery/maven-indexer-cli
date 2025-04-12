package com.liarstudio.maven_indexer.models

data class IndexedArtifact(
    val groupId: String,
    val artifactId: String,
    val version: String,
) {

    override fun toString(): String {
        return "$groupId:$artifactId:$version"
    }
}