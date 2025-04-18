package com.liarstudio.maven_indexer.models

data class ArtifactMetadata(
    val versions: List<String>,
    val latestVersion: String?,
    val releaseVersion: String?,
)