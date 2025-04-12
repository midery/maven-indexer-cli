package com.liarstudio.maven_indexer.indexer.data

import org.jetbrains.exposed.sql.Table

object ArtifactFtsDao : Table("artifacts_fts") {
    val rowid = integer("rowid") // автоматически соответствует Versions.id
    val groupId = varchar("group_id", 255)
    val artifactId = varchar("artifact_id", 255)
}