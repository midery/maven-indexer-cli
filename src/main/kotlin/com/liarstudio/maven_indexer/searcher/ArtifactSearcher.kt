package com.liarstudio.maven_indexer.searcher

import com.liarstudio.maven_indexer.indexer.data.IndexedArtifactDao
import com.liarstudio.maven_indexer.models.IndexedArtifact
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

class ArtifactSearcher {

    // TODO: Search with ranks: ORDER BY CASE -> then FTS25
    fun search(query: String, limit: Int = 50): List<IndexedArtifact> {
        return transaction {
            IndexedArtifactDao.select {
                (IndexedArtifactDao.groupId like "%$query%") or (IndexedArtifactDao.artifactId like "%$query%")
            }
                .limit(limit)
                .map {
                    IndexedArtifact(
                        groupId = it[IndexedArtifactDao.groupId],
                        artifactId = it[IndexedArtifactDao.artifactId],
                        version = it[IndexedArtifactDao.version]
                    )
                }
        }
    }
}