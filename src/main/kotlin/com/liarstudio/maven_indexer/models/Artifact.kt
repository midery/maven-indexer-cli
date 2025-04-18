package com.liarstudio.maven_indexer.models

data class Artifact(
    val groupId: String,
    val artifactId: String,
) : Comparable<Artifact> {

    override fun compareTo(other: Artifact): Int {
        return toString().compareTo(other.toString())
    }

    override fun toString(): String = "$groupId:$artifactId"
}