package com.liarstudio.maven_indexer

import com.liarstudio.maven_indexer.indexer.MultipleArtifactIndexer
import com.liarstudio.maven_indexer.indexer.CsvArtifactIndexer
import com.liarstudio.maven_indexer.indexer.FullMavenArtifactIndexer
import com.liarstudio.maven_indexer.data.network.NetworkClient
import com.liarstudio.maven_indexer.indexer.SingleArtifactIndexer
import com.liarstudio.maven_indexer.data.storage.ArtifactStorage
import com.liarstudio.maven_indexer.indexer.kmp.ArtifactKmpTargetsExtractor
import com.liarstudio.maven_indexer.indexer.kmp.ArtifactKmpTargetsExtractor.Companion.isKmpVariationOf
import com.liarstudio.maven_indexer.parser.CsvArtifactsParser
import com.liarstudio.maven_indexer.parser.MavenMetadataParser
import com.liarstudio.maven_indexer.parser.WebPageLinkUrlParser
import com.liarstudio.maven_indexer.parser.parseArtifact
import com.liarstudio.maven_indexer.printer.ProgressRenderer
import kotlinx.cli.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn")
    val parser = ArgParser("maven-indexer")

    val index by parser.option(
        ArgType.Boolean, shortName = "i", description = "Index all dependencies from Maven Central"
    )
    val search by parser.option(
        ArgType.String, shortName = "s", description = "Search artifact by name (group or artifactId)"
    )
    val indexArtifact by parser.option(
        ArgType.String, shortName = "ia", description = "Index single artifact. Input format: group:artifactId"
    )
    val indexFromCsv by parser.option(
        ArgType.String,
        shortName = "icsv",
        description = "Index from CSV file. CSV should be specified in input format with two columns: 'namespace' for artifact's groupId and 'name' for artifactId. \n" +
                "This program will try to index all the artifacts from the CSV file, as well as their KMP targets.\nFor example, if you add a row with 'ktor-network' library, " +
                "it will index all the possible kmp targets: \n* ktor-network-js, \n* ktor-network-jvm, \n* ktor-network-iosx64, etc."
    )
    val targets by parser.option(
        ArgType.String,
        shortName = "t",
        description = "Find single artifact's available Kotlin Multiplatform Targets. Input format: group:artifactId"
    )
    val versions by parser.option(
        ArgType.String,
        shortName = "v",
        description = "Find single artifact's available Versions. Input format: group:artifactId"
    )

    parser.parse(args)
    val progressRenderer = ProgressRenderer()
    val networkClient = NetworkClient()
    val artifactStorage = ArtifactStorage()

    artifactStorage.initialize()

    val indexer = SingleArtifactIndexer(MavenMetadataParser(networkClient), artifactStorage)

    runBlocking {
        when {
            index == true -> processArtifactsIndexing(
                indexer = FullMavenArtifactIndexer(
                    indexer = indexer, webPageLinkUrlParser = WebPageLinkUrlParser(networkClient)
                ),
                progressRenderer = progressRenderer
            )

            indexArtifact != null -> {
                indexer.indexArtifact(parseArtifact(indexArtifact!!))
            }

            indexFromCsv != null -> processArtifactsIndexing(
                indexer = CsvArtifactIndexer(
                    csvFile = File(indexFromCsv!!),
                    indexer = indexer,
                    csvParser = CsvArtifactsParser(),
                    kmpVariantsExtractor = ArtifactKmpTargetsExtractor(WebPageLinkUrlParser(networkClient)),
                ),
                progressRenderer = progressRenderer
            )

            search != null -> processArtifactSearch(search!!, artifactStorage)
            targets != null -> processAvailableTargets(targets!!, artifactStorage)
            versions != null -> processAvailableVersions(versions!!, artifactStorage)
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
    artifactInfo: String, artifactStorage: ArtifactStorage
) {
    val originalArtifact = parseArtifact(artifactInfo)
    val targets = artifactStorage.getArtifactTargets(artifact = originalArtifact)
        .filter { it.artifactId.isKmpVariationOf(originalArtifact) }
    if (targets.isEmpty()) {
        println("No Kotlin Multiplatform Targets found for '$artifactInfo'")
        return
    } else {
        println("All Kotlin Multiplatform Targets for '$artifactInfo': ")
        targets
            .sorted()
            .forEach { println("* $it") }
    }
}

private fun processAvailableVersions(
    artifactInfo: String, artifactStorage: ArtifactStorage
) {
    val versionsMeta = artifactStorage.getArtifactVersions(artifact = parseArtifact(artifactInfo))
    if (versionsMeta.versions.isEmpty()) {
        println("No versions found")
        return
    } else {
        println("All Versions for '$artifactInfo': ")
        versionsMeta.versions.sortedDescending()
            .forEach { version ->
                val versionSuffix = when (version) {
                    versionsMeta.latestVersion -> "(latest)"
                    versionsMeta.releaseVersion -> "(release)"
                    else -> ""
                }
                println("* $version $versionSuffix")
            }
    }

}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
private suspend fun processArtifactsIndexing(indexer: MultipleArtifactIndexer, progressRenderer: ProgressRenderer) {
    println("Start artifacts indexing...")
    indexer.index()
        .flowOn(Dispatchers.IO)
        .throttleLatest(1.seconds)
        .collect { progress ->
            progressRenderer.render(progress)
        }

}

fun <T> Flow<T>.throttleLatest(duration: Duration): Flow<T> = this
    .conflate()
    .transform {
        emit(it)
        delay(duration)
    }
