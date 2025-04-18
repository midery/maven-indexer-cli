package com.liarstudio.maven_indexer

import com.liarstudio.maven_indexer.indexer.MultipleArtifactIndexer
import com.liarstudio.maven_indexer.indexer.CsvArtifactIndexer
import com.liarstudio.maven_indexer.indexer.FullMavenArtifactIndexer
import com.liarstudio.maven_indexer.data.network.NetworkClient
import com.liarstudio.maven_indexer.indexer.SingleArtifactIndexer
import com.liarstudio.maven_indexer.data.storage.ArtifactStorage
import com.liarstudio.maven_indexer.parser.MavenMetadataParser
import com.liarstudio.maven_indexer.parser.WebPageLinkUrlParser
import com.liarstudio.maven_indexer.parser.parseArtifact
import kotlinx.cli.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import java.io.File

fun main(args: Array<String>) {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn")
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
    val networkClient = NetworkClient()
    val artifactStorage = ArtifactStorage()

    artifactStorage.initialize()

    val indexer = SingleArtifactIndexer(MavenMetadataParser(networkClient), artifactStorage)

    runBlocking {
        when {
            index == true -> processArtifactsIndexing(
                FullMavenArtifactIndexer(
                    indexer = indexer,
                    webPageLinkUrlParser = WebPageLinkUrlParser(networkClient)
                )
            )

            indexArtifact != null -> {
                indexer.indexArtifact(parseArtifact(indexArtifact!!))
            }

            indexFromCsv != null -> processArtifactsIndexing(
                CsvArtifactIndexer(File(indexFromCsv!!), indexer)
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
private suspend fun processArtifactsIndexing(indexer: MultipleArtifactIndexer) {
    println("Start artifacts indexing...")
    indexer.index()
        .flowOn(Dispatchers.IO)
        .collect { progress ->
            if (progress.total != null) {
                print("\rProgress: ${progress.current}/${progress.total} artifacts")
            } else {
                print("\rProgress: ${progress.current} artifacts...")
            }
        }
    println()
    println("✅ Done indexing all artifacts!")
    println()
}
