package com.liarstudio.maven_indexer.models

data class VersionMetadata(
    val versions: List<String>,
    val latestVersion: String?,
    val releaseVersion: String?,
)