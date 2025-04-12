package com.liarstudio.maven_indexer.indexer.data

import com.liarstudio.maven_indexer.indexer.data.ArtifactDao.artifactId
import com.liarstudio.maven_indexer.indexer.data.ArtifactDao.groupId
import com.liarstudio.maven_indexer.models.Artifact
import com.liarstudio.maven_indexer.models.IndexedArtifact
import com.liarstudio.maven_indexer.models.VersionMetadata
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class ArtifactRepository {

    val mutex = Mutex()

    suspend fun saveArtifact(artifact: Artifact, versionsMetadata: VersionMetadata) {
        mutex.withLock {
            transaction {
                val artifactDatabaseId = updateArtifact(artifact)
                updateVersions(
                    artifactDatabaseId = artifactDatabaseId,
                    versionsMetadata = versionsMetadata,
                )
            }
        }
    }

    private fun updateArtifact(artifact: Artifact): Long {
        val dbArtifact =
            ArtifactDao.select(groupId.eq(artifact.groupId) and artifactId.eq(artifact.artifactId))
                .firstOrNull()

        return if (dbArtifact == null) {
            ArtifactDao.insertAndGetId {
                it[groupId] = artifact.groupId
                it[artifactId] = artifact.artifactId
            }
        } else {
            dbArtifact[ArtifactDao.id]
        }
            .value

    }

    private fun updateVersions(
        artifactDatabaseId: Long,
        versionsMetadata: VersionMetadata,
    ) {
        val existingVersions = VersionDao
            .select { VersionDao.artifact eq artifactDatabaseId }
            .map { it[VersionDao.version] }
            .toSet()

        val newVersions = versionsMetadata.versions
            .filter { it !in existingVersions }
            .distinctBy { it }

        VersionDao.batchInsert(newVersions) { version ->
            this[VersionDao.artifact] = artifactDatabaseId
            this[VersionDao.version] = version.take(100)
        }

        val latestVersion = versionsMetadata.latestVersion
        if (latestVersion != null) {
            updateVersionMetadata(latestVersion, VersionDao.isLatest, artifactDatabaseId)
        }

        val releaseVersion = versionsMetadata.releaseVersion
        if (releaseVersion != null) {
            updateVersionMetadata(releaseVersion, VersionDao.isRelease, artifactDatabaseId)
        }

        syncFTSIndex()

    }

    fun syncFTSIndex() = transaction {
        exec(
            """
        INSERT INTO artifacts_fts(rowid, group_id, artifact_id)
        SELECT a.id, a.group_id, a.artifact_id
        FROM artifacts a
        WHERE a.id NOT IN (SELECT rowid FROM artifacts_fts);
    """.trimIndent()
        )
    }

    private fun updateVersionMetadata(
        versionToCheck: String,
        columnToUpdate: Column<Boolean>,
        artifactDatabaseId: Long,
    ) {
        VersionDao.update({ VersionDao.artifact eq artifactDatabaseId }) {
            it[columnToUpdate] = false
        }
        VersionDao.update({
            (VersionDao.artifact eq artifactDatabaseId) and
                    (VersionDao.version.eq(versionToCheck))
        }) {
            it[columnToUpdate] = true
        }
    }

    suspend fun getArtifacts(query: String, limit: Int = 50): List<IndexedArtifact> =
        transaction {
            exec(
                """
        SELECT a.group_id, a.artifact_id, v.version
        FROM artifacts_fts f
        JOIN versions v ON f.rowid = v.id
        JOIN artifacts a ON a.id = v.artifact
        WHERE artifacts_fts MATCH 'kotlin'
          AND v.is_latest = 1
    """.trimIndent(),

            ) {
                val results = mutableListOf<IndexedArtifact>()
                while (it.next()) {
                    IndexedArtifact(
                        groupId = it.getString("group_id"),
                        artifactId = it.getString("artifact_id"),
                        version = it.getString("version")
                    )
                }
                results
            }
        } ?: emptyList()
}