package com.liarstudio.maven_indexer.parser

import com.liarstudio.maven_indexer.models.Artifact

val Artifact.groupIdUrlPath get() = groupId.replace('.', '/')
val Artifact.urlPath get() = "$groupIdUrlPath/$artifactId"
