package com.liarstudio.maven_indexer.indexer.data
import org.jetbrains.exposed.sql.Table

object IndexedArtifactDao : Table("artifacts") {
    val id = integer("id").autoIncrement()
    val groupId = varchar("group_id", 255)
    val artifactId = varchar("artifact_id", 255)
    val version = varchar("version", 100)

    override val primaryKey = PrimaryKey(id)
}
