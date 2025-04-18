package com.liarstudio.maven_indexer.data.storage

import org.jetbrains.exposed.dao.id.LongIdTable

object ArtifactDao : LongIdTable("artifacts") {
    val groupId = varchar("group_id", 255)
    val artifactId = varchar("artifact_id", 255)


    init {
        index(true, artifactId, groupId) // Индекс для ускорения поиска по артефакту и версии
    }
}
