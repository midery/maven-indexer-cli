package com.liarstudio.maven_indexer

import com.liarstudio.maven_indexer.crawler.CsvArtifactCrawler
import com.liarstudio.maven_indexer.crawler.FullMavenArtifactCrawler
import com.liarstudio.maven_indexer.indexer.ArtifactIndexer
import com.liarstudio.maven_indexer.indexer.data.IndexedArtifactDao
import com.liarstudio.maven_indexer.models.Artifact
import com.liarstudio.maven_indexer.searcher.ArtifactSearcher
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
        ArgType.String,
        shortName = "ia",
        description = "Index single artifact (group:artifactId)"
    )
    val indexFromCsv by parser.option(ArgType.String, shortName = "icsv", description = "Index from CSV file")

    parser.parse(args)

    val dotenv = dotenv { ignoreIfMissing = true }
    val dbPath = dotenv["DB_PATH"] ?: "maven.db"
    Database.connect("jdbc:sqlite:$dbPath", driver = "org.sqlite.JDBC")
    transaction { SchemaUtils.create(IndexedArtifactDao) }


    val indexer = ArtifactIndexer()


    runBlocking {
        when {
            index == true -> FullMavenArtifactCrawler().crawlAndIndex(indexer)
            indexArtifact != null -> {
                val (groupId, artifactId) = indexArtifact!!.split(":")
                indexer.indexArtifact(Artifact(artifactId = artifactId, groupId = groupId))
            }

            indexFromCsv != null -> CsvArtifactCrawler(File(indexFromCsv!!)).crawlAndIndex(indexer)
            search != null -> ArtifactSearcher().search(search!!)
                .map { result ->  println("$result") }
            else -> println("Use --help to see options.")
        }
    }
}
