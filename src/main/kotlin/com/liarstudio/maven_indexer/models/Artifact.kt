package com.liarstudio.maven_indexer.models

data class Artifact(
    val groupId: String,
    val artifactId: String,
) {
    override fun toString(): String = "$groupId:$artifactId"
}