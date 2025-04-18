package com.liarstudio.maven_indexer.models

data class ArtifactVersionMetadata(
    val versions: List<String>,
    val latestVersion: String?,
    val releaseVersion: String?,
)