package com.liarstudio.maven_indexer.indexer.data

import com.liarstudio.maven_indexer.indexer.data.ArtifactDao.artifactId
import com.liarstudio.maven_indexer.indexer.data.ArtifactDao.groupId
import com.liarstudio.maven_indexer.models.Artifact
import com.liarstudio.maven_indexer.models.IndexedArtifact
import com.liarstudio.maven_indexer.models.VersionMetadata
import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class ArtifactStorage {

    val mutex = Mutex()

    fun initialize() {
        val dotenv = dotenv { ignoreIfMissing = true }
        val dbPath = dotenv["DB_PATH"] ?: "maven.db"
        Database.connect("jdbc:sqlite:$dbPath", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(ArtifactDao, VersionDao)
            exec(
                """
                        CREATE VIRTUAL TABLE IF NOT EXISTS artifacts_fuzzy_index
                        USING fts5(group_id, artifact_id, content='', columnsize=1);
                """
            )
        }
    }

    suspend fun saveArtifact(artifact: Artifact, versionsMetadata: VersionMetadata) {
        mutex.withLock {
            transaction {
                val artifactDatabaseId = updateArtifact(artifact)
                updateArtifactTrigrams(artifact, artifactDatabaseId)
                updateVersions(
                    artifactDatabaseId = artifactDatabaseId,
                    versionsMetadata = versionsMetadata,
                )
            }
        }
    }

    private fun Transaction.updateArtifactTrigrams(artifact: Artifact, artifactDatabaseId: Long) {
        val trigramGroup = generateTrigramString(artifact.groupId)
        val trigramArtifact = generateTrigramString(artifact.artifactId)
        val fuzzyIndexAlreadyExists = exec(
            """
            SELECT 1 FROM artifacts_fuzzy_index WHERE rowid = $artifactDatabaseId
            """
        ) { it.next() } == true

        if (!fuzzyIndexAlreadyExists) {
            exec(
                """
        INSERT INTO artifacts_fuzzy_index(rowid, group_id, artifact_id)
        VALUES ($artifactDatabaseId, '$trigramGroup', '$trigramArtifact')
    """
            )
        }
    }

    private fun generateTrigramString(word: String, separator: String = " "): String {
        val input = word.lowercase().normalize()
        return (0..input.length - 3).joinToString(separator) { i ->
            input.substring(i, i + 3)
        }
    }

    fun String.normalize(): String {
        return filter(Char::isLetterOrDigit)
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

    fun getArtifacts(query: String, limit: Int = 50): List<IndexedArtifact> =
        transaction {
            val trigrams = generateTrigramString(query, " OR ")
            exec(
                """
        SELECT a.group_id, a.artifact_id, v.version, bm25(artifacts_fuzzy_index) as score
        FROM artifacts_fuzzy_index f
        JOIN artifacts a ON a.id = f.rowid
        JOIN versions v ON a.id = v.artifact
        WHERE artifacts_fuzzy_index MATCH '$trigrams'
          AND v.is_latest = 1
        ORDER by score ASC
        LIMIT $limit
    """.trimIndent(),
            ) {
                val results = mutableListOf<IndexedArtifact>()
                while (it.next()) {
                    results += IndexedArtifact(
                        groupId = it.getString("group_id"),
                        artifactId = it.getString("artifact_id"),
                        version = it.getString("version")
                    )
                }
                results
            }
        } ?: emptyList()

    fun getArtifactTargets(artifact: Artifact): List<String> = transaction {
        ArtifactDao.select { (artifactId like "${artifact.artifactId}%") and (groupId eq artifact.groupId) }
            .mapNotNull { it[artifactId].takeIf { id -> id != artifact.artifactId } }
            .map { it.split('-').last() }
    }
}