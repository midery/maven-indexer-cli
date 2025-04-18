package com.liarstudio.maven_indexer.data.storage

import org.jetbrains.exposed.dao.id.LongIdTable

object VersionDao : LongIdTable("versions") {
    val artifact = reference("artifact", ArtifactDao)
    val version = varchar("version", 100)

    // TODO: Should be included in idx?
    val isLatest = bool("is_latest").default(false)
    val isRelease = bool("is_release").default(false)


    init {
        index(true, artifact, version) // Индекс для ускорения поиска по артефакту и версии
    }
}


