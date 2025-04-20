package com.liarstudio.maven_indexer.data.storage

import com.liarstudio.maven_indexer.models.Artifact
import com.liarstudio.maven_indexer.models.VersionedArtifact
import com.liarstudio.maven_indexer.models.ArtifactVersionMetadata
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.sql.DriverManager

/**
 * Storage for artifacts and versions.
 *
 * Can be configured to store results both in-memory and on disk.
 *
 * You should call [ArtifactStorage.initialize] before any other method.
 */
class ArtifactStorage {

    private val mutex = Mutex()

    /**
     * Initializes storage so it is ready to be used.
     *
     * @param storeStrategy defines how should we store the data: either on DISK, or in MEMORY.
     */
    fun initialize(storeStrategy: StoreStrategy = StoreStrategy.DISK) {
        val connectUrl = when (storeStrategy) {
            StoreStrategy.DISK -> {
                "jdbc:sqlite:maven.db"
            }

            StoreStrategy.IN_MEMORY -> {
                "jdbc:sqlite:file:test?mode=memory&cache=shared"
                    // Trick to enable in-memory DB connection in Unit tests
                    .also(DriverManager::getConnection)
            }
        }
        val driver = "org.sqlite.JDBC"
        Database.connect(connectUrl, driver)
        transaction {
            SchemaUtils.create(ArtifactDao, VersionDao)
            exec(
                """
                        CREATE VIRTUAL TABLE IF NOT EXISTS artifacts_fuzzy_index
                        USING fts5(group_id, artifact_id, tokenize="trigram");
                """
            )
        }
    }

    /**
     * Saves an artifact and it's meta information to the storage.
     *
     * This method saves:
     * * An artifact information to one table
     * * Version metadata about the artifact
     * * Search metadata for the artifact, that helps to get faster ranked search by group/artifactId
     */
    suspend fun saveArtifact(artifact: Artifact, versionsMetadata: ArtifactVersionMetadata) {
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

    /**
     * Gets number of artifacts stored.
     */
    fun getArtifactsCount(): Int = transaction {
        ArtifactDao
            .slice(ArtifactDao.id.count())
            .selectAll()
            .single()[ArtifactDao.id.count()]
            .toInt()
    }

    /**
     * Gets artifacts in batches.
     */
    fun getArtifacts(limit: Int, offset: Long): List<Artifact> = transaction {
        ArtifactDao.selectAll()
            .limit(limit, offset)
            .map {
                Artifact(it[ArtifactDao.groupId], it[ArtifactDao.artifactId])
            }
    }

    /**
     * Searches for artifacts with a [query].
     *
     * Results are ranked according to a relevance to a query, using Sqlite FTS5 rank function.
     *
     * More information: [https://sqlite.org/fts5.html](https://sqlite.org/fts5.html).
     *
     * @param query search request. Will be normalized via [normalize] function before being added to a DB query.
     * @param limit number of search results displayed.
     *
     * @return list of artifacts with their latest release versions displayed.
     */
    fun searchArtifacts(query: String, limit: Int = 50): List<VersionedArtifact> =
        transaction {
            val normalizedQuery = query.normalize()
            exec(
                """
        SELECT a.group_id, a.artifact_id, v.version
        FROM artifacts_fuzzy_index f
        JOIN artifacts a ON a.id = f.rowid
        JOIN versions v ON a.id = v.artifact
        WHERE artifacts_fuzzy_index MATCH '$normalizedQuery'
          AND v.is_release = 1
        ORDER by rank ASC
        LIMIT $limit
    """.trimIndent(),
            ) {
                val results = mutableListOf<VersionedArtifact>()
                while (it.next()) {
                    results += VersionedArtifact(
                        groupId = it.getString("group_id"),
                        artifactId = it.getString("artifact_id"),
                        version = it.getString("version")
                    )
                }
                results
            }
        } ?: emptyList()

    /**
     * Gets Kotlin Multiplatform Targets of a target [artifact].
     *
     * @return list of targeted artifacts
     */
    fun getArtifactTargets(artifact: Artifact): List<Artifact> = transaction {
        ArtifactDao.select { (ArtifactDao.artifactId like "${artifact.artifactId}%") and (ArtifactDao.groupId eq artifact.groupId) }
            .mapNotNull {
                val groupId = it[ArtifactDao.groupId]
                val artifactId = it[ArtifactDao.artifactId]
                if (artifactId != artifact.artifactId) {
                    Artifact(groupId, artifactId)
                } else {
                    null
                }
            }
    }

    /**
     * Gets the list of artifact versions.
     */
    fun getArtifactVersions(artifact: Artifact): ArtifactVersionMetadata = transaction {
        var releaseVersion: String? = null
        var latestVersion: String? = null
        val versions = (ArtifactDao innerJoin VersionDao)
            .select {
                (ArtifactDao.artifactId eq artifact.artifactId) and (ArtifactDao.groupId eq artifact.groupId)
            }
            .map {
                val version = it[VersionDao.version]
                if (it[VersionDao.isLatest]) {
                    latestVersion = version
                }
                if (it[VersionDao.isRelease]) {
                    releaseVersion = version
                }
                version
            }
        ArtifactVersionMetadata(versions, latestVersion, releaseVersion)
    }


    /**
     * Searches for an artifact in the table, and if it exists.
     * Otherwise, inserts a new entry in the table.
     *
     * @return database identifier of this artifact
     */
    private fun updateArtifact(artifact: Artifact): Long {
        val dbArtifact =
            ArtifactDao.select(ArtifactDao.groupId.eq(artifact.groupId) and ArtifactDao.artifactId.eq(artifact.artifactId))
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

    /**
     * Updates artifact's versions.
     *
     * Does not override previously saved versions of an artifact,
     * only adds a new versions, and updates isRelease/isLatest fields to be actual.
     */
    private fun updateVersions(
        artifactDatabaseId: Long,
        versionsMetadata: ArtifactVersionMetadata,
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

    /**
     * Updates single boolean [columnToUpdate] in according to [versionToCheck]:
     *
     * * All the rows which do not equal to [versionToCheck] will have false.
     * * Only one row which is equal to [versionToCheck] will have true.
     */
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

    /**
     * Updates artifact's search metadata.
     *
     * If it is already saved - skips the action.
     *
     * If not - normalizes the string via [normalize] function, and inserts groupId + artifactId in the FTS5 table
     * for better access and ranking.
     */
    private fun Transaction.updateArtifactTrigrams(artifact: Artifact, artifactDatabaseId: Long) {
        val artifactSearchEntryAlreadyExists = exec(
            """
            SELECT 1 FROM artifacts_fuzzy_index WHERE rowid = $artifactDatabaseId
            """
        ) { it.next() } == true

        if (!artifactSearchEntryAlreadyExists) {
            exec(
                """
        INSERT INTO artifacts_fuzzy_index(rowid, group_id, artifact_id)
        VALUES ($artifactDatabaseId, '${artifact.groupId.normalize()}', '${artifact.artifactId.normalize()}')
    """
            )
        }
    }

    /**
     * Normalizes a string to store/search in a Full-Text-Search database.
     *
     * * Input: `io.ktor:ktor-network`
     * * Output: `ioktorktornetwork`
     */
    fun String.normalize(): String {
        return lowercase().filter(Char::isLetterOrDigit)
    }

    enum class StoreStrategy {
        DISK, IN_MEMORY
    }
}