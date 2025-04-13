package com.liarstudio.maven_indexer

import com.liarstudio.maven_indexer.crawler.CsvArtifactCrawler
import com.liarstudio.maven_indexer.crawler.FullMavenArtifactCrawler
import com.liarstudio.maven_indexer.indexer.ArtifactIndexer
import com.liarstudio.maven_indexer.indexer.data.ArtifactDao
import com.liarstudio.maven_indexer.indexer.data.ArtifactRepository
import com.liarstudio.maven_indexer.indexer.data.VersionDao
import com.liarstudio.maven_indexer.parser.parseArtifact
import kotlinx.cli.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.runBlocking
import java.io.File

fun main(args: Array<String>) {
    val parser = ArgParser("maven-indexer")

    val index by parser.option(ArgType.Boolean, shortName = "i", description = "Index all from Maven Central")
    val search by parser.option(ArgType.String, shortName = "s", description = "Search artifact by name")
    val indexArtifact by parser.option(
        ArgType.String, shortName = "ia", description = "Index single artifact (group:artifactId)"
    )
    val indexFromCsv by parser.option(ArgType.String, shortName = "icsv", description = "Index from CSV file")
    val targets by parser.option(
        ArgType.String,
        shortName = "t",
        description = "Find single artifact's Kotlin Multiplatform Targets"
    )

    parser.parse(args)
    val dotenv = dotenv { ignoreIfMissing = true }
    val dbPath = dotenv["DB_PATH"] ?: "maven.db"
    Database.connect("jdbc:sqlite:$dbPath", driver = "org.sqlite.JDBC")
    transaction { SchemaUtils.create(ArtifactDao, VersionDao) }


    val artifactRepository = ArtifactRepository()
    val indexer = ArtifactIndexer(artifactRepository)
    runBlocking {
        when {
            index == true -> FullMavenArtifactCrawler().crawlAndIndex(indexer)

            indexArtifact != null -> {
                indexer.indexArtifact(parseArtifact(indexArtifact!!))
            }

            indexFromCsv != null -> CsvArtifactCrawler(File(indexFromCsv!!)).crawlAndIndex(indexer)
            search != null -> artifactRepository.getArtifacts(search!!, limit = 50).mapIndexed { i, result ->
                val printingIndex = if (i < 9) "0${i + 1}" else "${i + 1}"
                println("$printingIndex. $result")
            }

            targets != null -> processAvailableTargets(targets!!, artifactRepository)

            else -> println("Use --help to see options.")
        }
    }
}

private fun processAvailableTargets(
    artifactInfo: String,
    artifactRepository: ArtifactRepository
) {
    val targets = artifactRepository.getArtifactTargets(artifact = parseArtifact(artifactInfo))
    if (targets.isEmpty()) {
        println("No targets found")
        return
    } else {
        println("Kotlin Multiplatform Targets for $artifactInfo: ")
        targets.sorted().forEach(::println)
    }
}
