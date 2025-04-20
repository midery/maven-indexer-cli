package com.liarstudio.maven_indexer.data

import com.liarstudio.maven_indexer.data.storage.ArtifactDao
import com.liarstudio.maven_indexer.data.storage.ArtifactStorage
import com.liarstudio.maven_indexer.data.storage.ArtifactStorage.StoreStrategy
import com.liarstudio.maven_indexer.data.storage.VersionDao
import com.liarstudio.maven_indexer.models.Artifact
import com.liarstudio.maven_indexer.models.ArtifactVersionMetadata
import com.liarstudio.maven_indexer.models.VersionedArtifact
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

class ArtifactStorageTest {

    private val storage = ArtifactStorage()

    @BeforeEach
    fun setUp() {
        storage.initialize(StoreStrategy.IN_MEMORY)
    }

    @AfterEach
    fun cleanUp() {
        transaction {
            SchemaUtils.drop(ArtifactDao, VersionDao)
            exec("DROP TABLE IF EXISTS artifacts_fuzzy_index")
        }
    }

    @Test
    fun `GIVEN artifact WHEN saved THEN appears in table`() = runTest {
        val artifact = Artifact("com.test", "lib")
        val metadata = ArtifactVersionMetadata(listOf("1.0.0"), latestVersion = "1.0.0", releaseVersion = "1.0.0")

        storage.saveArtifact(artifact, metadata)

        val saved = storage.getArtifactVersions(artifact)
        assertEquals(metadata, saved)
    }


    @Test
    fun `GIVEN indexed artifact WHEN searched with trigram THEN returned_library`() = runTest {
        val artifact = Artifact("com.trigram", "searchlib")
        val metadata = ArtifactVersionMetadata(listOf("1.2.3"), latestVersion = "1.2.3", releaseVersion = "1.2.3")
        val expectedResult = VersionedArtifact("com.trigram", "searchlib", "1.2.3")

        storage.saveArtifact(artifact, metadata)

        val results = storage.searchArtifacts("rchli")
        assertEquals(listOf(expectedResult), results)
    }

    @Test
    @Disabled("Fuzzy search with allowed mistakes is not yet implemented")
    fun `GIVEN indexed artifact WHEN fuzzy searched with trigram THEN returned_library`() = runTest {
        val artifact = Artifact("com.trigram", "searchlib")
        val metadata = ArtifactVersionMetadata(listOf("1.2.3"), latestVersion = "1.2.3", releaseVersion = "1.2.3")
        val expectedResult = VersionedArtifact("com.trigram", "searchlib", "1.2.3")

        storage.saveArtifact(artifact, metadata)

        val results = storage.searchArtifacts("serchlip")
        assertEquals(listOf(expectedResult), results)
    }

    @Test
    fun `GIVEN multiple artifacts WHEN paged THEN returns correct page`() = runTest {
        val artifacts = (1..5).map {
            Artifact("com.paging", "lib$it") to ArtifactVersionMetadata(listOf("1.0.$it"), "1.0.$it", "1.0.$it")
        }

        artifacts.forEach { (artifact, meta) -> storage.saveArtifact(artifact, meta) }

        val result = storage.getArtifacts(limit = 2, offset = 2)
        assertEquals(2, result.size)
        assertEquals("lib3", result.first().artifactId)
    }

    @Test
    fun `GIVEN artifact saved twice WHEN fetched THEN only one copy exists`() = runTest {
        val artifact = Artifact("org.dupe", "lib")
        val meta = ArtifactVersionMetadata(listOf("1.0"), "1.0", "1.0")

        storage.saveArtifact(artifact, meta)
        storage.saveArtifact(artifact, meta)

        val all = storage.getArtifacts(limit = 10, offset = 0)
        assertEquals(1, all.size)
    }

    @Test
    fun `GIVEN artifact with variants WHEN fetched targets THEN returns only variants`() = runTest {
        val base = Artifact("org.kmp", "base")
        val variant = Artifact("org.kmp", "base-jvm")

        val meta = ArtifactVersionMetadata(listOf("1.0"), "1.0", "1.0")
        storage.saveArtifact(base, meta)
        storage.saveArtifact(variant, meta)

        val targets = storage.getArtifactTargets(base)
        assertEquals(listOf(variant), targets)
    }

    @Test
    fun `GIVEN different versions WHEN updating latest THEN sets flag correctly`() = runTest {
        val artifact = Artifact("org.version", "multi")
        val meta1 = ArtifactVersionMetadata(listOf("1.0.0"), "1.0.0", null)
        val updatedMeta = ArtifactVersionMetadata(listOf("1.0.0", "2.0.0"), "2.0.0", null)

        storage.saveArtifact(artifact, meta1)
        assertEquals(
            "1.0.0",
            storage.getArtifactVersions(artifact).latestVersion
        )

        storage.saveArtifact(artifact, updatedMeta)

        assertEquals(
            "2.0.0",
            storage.getArtifactVersions(artifact).latestVersion
        )
    }

    @Test
    fun `GIVEN 10_000 artifacts WHEN fuzzy searched THEN returns matches quickly`() = runTest {
        val baseGroup = "org.large"
        val artifactPrefix = "lib"
        val needle = "ser"

        val needleArtifact = Artifact(baseGroup, "kotlinx-serialization-core")
        val needleMeta = ArtifactVersionMetadata(listOf("1.7.0"), latestVersion = "1.7.0", releaseVersion = "1.7.0")

        (1..9999).forEach { i ->
            val artifact = Artifact(baseGroup, "$artifactPrefix$i")
            val meta = ArtifactVersionMetadata(listOf("0.0.$i"), latestVersion = "0.0.$i", releaseVersion = "0.0.$i")
            storage.saveArtifact(artifact, meta)
            if (i == 4999) {
                storage.saveArtifact(needleArtifact, needleMeta)
            }
        }

        val elapsed = measureTimeMillis {
            val results = storage.searchArtifacts(needle)
            assertEquals(results.single(), VersionedArtifact(baseGroup, "kotlinx-serialization-core", "1.7.0"))
        }

        println("Fuzzy search completed in $elapsed ms")
        assertTrue(elapsed < 100, "Fuzzy search completed should be completed in >100ms")
    }

}
