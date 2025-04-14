package com.liarstudio.maven_indexer

import com.liarstudio.maven_indexer.crawler.ArtifactCrawler
import com.liarstudio.maven_indexer.crawler.CsvArtifactCrawler
import com.liarstudio.maven_indexer.crawler.FullMavenArtifactCrawler
import com.liarstudio.maven_indexer.indexer.ArtifactIndexer
import com.liarstudio.maven_indexer.indexer.data.ArtifactStorage
import com.liarstudio.maven_indexer.parser.parseArtifact
import kotlinx.cli.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import java.io.File

fun main(args: Array<String>) {
    val parser = ArgParser("maven-indexer")

    val index by parser.option(
        ArgType.Boolean,
        shortName = "i",
        description = "Index all dependencies from Maven Central"
    )
    val search by parser.option(
        ArgType.String,
        shortName = "s",
        description = "Search artifact by name (group or artifactId)"
    )
    val indexArtifact by parser.option(
        ArgType.String, shortName = "ia", description = "Index single artifact. Input format: group:artifactId"
    )
    val indexFromCsv by parser.option(ArgType.String, shortName = "icsv", description = "Index from CSV file")
    val targets by parser.option(
        ArgType.String,
        shortName = "t",
        description = "Find single artifact's available Kotlin Multiplatform Targets. Input format: group:artifactId"
    )

    parser.parse(args)
    val artifactStorage = ArtifactStorage()

    artifactStorage.initialize()

    val indexer = ArtifactIndexer(artifactStorage)

    runBlocking {
        when {
            index == true -> processArtifactsIndexing(FullMavenArtifactCrawler(indexer = indexer))

            indexArtifact != null -> {
                indexer.indexArtifact(parseArtifact(indexArtifact!!))
            }

            indexFromCsv != null -> processArtifactsIndexing(
                CsvArtifactCrawler(File(indexFromCsv!!), indexer)
            )

            search != null -> processArtifactSearch(search!!, artifactStorage)
            targets != null -> processAvailableTargets(targets!!, artifactStorage)
            else -> println("Use --help to see options.")
        }
    }
}

private fun processArtifactSearch(query: String, artifactStorage: ArtifactStorage) {
    artifactStorage.getArtifacts(query, limit = 50).mapIndexed { i, result ->
        val printingIndex = if (i < 9) "0${i + 1}" else "${i + 1}"
        println("$printingIndex. $result")
    }
}

private fun processAvailableTargets(
    artifactInfo: String,
    artifactStorage: ArtifactStorage
) {
    val targets = artifactStorage.getArtifactTargets(artifact = parseArtifact(artifactInfo))
    if (targets.isEmpty()) {
        println("No targets found")
        return
    } else {
        println("Kotlin Multiplatform Targets for $artifactInfo: ")
        targets.sorted().forEach(::println)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun processArtifactsIndexing(crawler: ArtifactCrawler) {
    println("Start artifacts indexing...")
    crawler.crawlAndIndex()
        .flowOn(Dispatchers.IO)
        .collect { progress ->
            if (progress.total != null) {
                print("\rProgress: ${progress.current}/${progress.total} artifacts")
            } else {
                print("\rProgress: ${progress.current} artifacts")
            }
        }
    println()
    println("✅ Done indexing all artifacts!")
    println()
}
