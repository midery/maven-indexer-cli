package com.liarstudio.maven_indexer

import com.liarstudio.maven_indexer.models.Artifact


const val MAVEN_CENTRAL_REPO_URL = "https://repo.maven.apache.org/maven2/"

val Artifact.groupIdUrlPath get() = groupId.replace('.', '/')
val Artifact.urlPath get() = "$groupIdUrlPath/$artifactId"
