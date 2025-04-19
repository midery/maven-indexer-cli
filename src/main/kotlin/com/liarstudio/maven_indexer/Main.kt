package com.liarstudio.maven_indexer

import com.liarstudio.maven_indexer.indexer.MultipleArtifactIndexer
import com.liarstudio.maven_indexer.indexer.CsvArtifactIndexer
import com.liarstudio.maven_indexer.indexer.FullMavenArtifactIndexer
import com.liarstudio.maven_indexer.data.network.NetworkClient
import com.liarstudio.maven_indexer.indexer.SingleArtifactIndexer
import com.liarstudio.maven_indexer.data.storage.ArtifactStorage
import com.liarstudio.maven_indexer.indexer.VersionOnlyArtifactIndexer
import com.liarstudio.maven_indexer.indexer.extractor.ArtifactKmpTargetsExtractor
import com.liarstudio.maven_indexer.indexer.extractor.ArtifactKmpTargetsExtractor.Companion.isKmpVariationOf
import com.liarstudio.maven_indexer.cli.logger.LogLevel
import com.liarstudio.maven_indexer.models.Artifact
import com.liarstudio.maven_indexer.cli.parser.ArtifactParamParser
import com.liarstudio.maven_indexer.indexer.parser.CsvArtifactsParser
import com.liarstudio.maven_indexer.indexer.extractor.MavenMetadataExtractor
import com.liarstudio.maven_indexer.indexer.extractor.HtmlPageLinkExtractor
import com.liarstudio.maven_indexer.indexer.parser.XmlMetadataParser
import com.liarstudio.maven_indexer.cli.printer.ProgressRenderer
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
    val parser = ArgParser("maven-indexer")

    val index by parser.option(
        ArgType.Boolean, shortName = "i", description = "Index all dependencies from Maven Central"
    )

    val indexGroup by parser.option(
        ArgType.String,
        shortName = "ig",
        description = "Index all dependencies for particular groupId from Maven Central." +
                "Example input: io.ktor"
    )

    val indexArtifact by parser.option(
        ArgType.String, shortName = "ia", description = "Index single artifact. Input format: group:artifactId"
    )
    val indexFromCsv by parser.option(
        ArgType.String,
        shortName = "icsv",
        description = "Index from CSV file. CSV should be specified in input format with two columns: 'namespace' for artifact's groupId and 'name' for artifactId. \n\t" +
                "This program will try to index all the artifacts from the CSV file, as well as their KMP targets.\n\tFor example, if you add a row with 'ktor-network' library, " +
                "it will index all the possible kmp targets: \n\t* ktor-network-js, \n\t* ktor-network-jvm, \n\t* ktor-network-iosx64, etc."
    )
    val refresh by parser.option(
        ArgType.Boolean,
        shortName = "r",
        description = "Refreshes already indexed artifact versions. This action should be faster than refreshing whole Maven Central, and can be performed periodically."
    )

    val search by parser.option(
        ArgType.String, shortName = "s", description = "Search artifact by name (group or artifactId)"
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

    val logLevel by parser.option(
        ArgType.Choice<LogLevel>(), shortName = "log", description = "Specife desired log  level. "
    )
        .default(LogLevel.warn)

    parser.parse(args)

    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", logLevel.name)

    val progressRenderer = ProgressRenderer()
    val networkClient = NetworkClient()
    val artifactStorage = ArtifactStorage()
    val parseArtifact = ArtifactParamParser()
    val xmlParser = XmlMetadataParser()

    artifactStorage.initialize()

    val indexer = SingleArtifactIndexer(MavenMetadataExtractor(networkClient, xmlParser), artifactStorage)

    runBlocking {

        when {
            index == true -> processArtifactsIndexing(
                indexer = FullMavenArtifactIndexer(
                    indexer = indexer,
                    htmlPageLinkExtractor = HtmlPageLinkExtractor(networkClient),
                ),
                progressRenderer = progressRenderer
            )

            indexGroup != null -> processArtifactsIndexing(
                indexer = FullMavenArtifactIndexer(
                    indexer = indexer,
                    htmlPageLinkExtractor = HtmlPageLinkExtractor(networkClient),
                    additionalPath = indexGroup!!.replace('.', '/') + '/'
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
                    kmpVariantsExtractor = ArtifactKmpTargetsExtractor(HtmlPageLinkExtractor(networkClient)),
                ), progressRenderer = progressRenderer
            )

            search != null -> processArtifactSearch(search!!, artifactStorage)
            refresh != null -> processArtifactsIndexing(
                indexer = VersionOnlyArtifactIndexer(
                    artifactStorage, indexer
                ),
                progressRenderer
            )

            targets != null -> processAvailableTargets(parseArtifact(targets!!), artifactStorage)
            versions != null -> processAvailableVersions(parseArtifact(versions!!), artifactStorage)
            else -> println("Use --help to see options.")
        }
    }
}

private fun processArtifactSearch(query: String, artifactStorage: ArtifactStorage) {
    artifactStorage.searchArtifacts(query, limit = 50).mapIndexed { i, result ->
        val printingIndex = if (i < 9) "0${i + 1}" else "${i + 1}"
        println("$printingIndex. $result")
    }
}

private fun processAvailableTargets(
    artifact: Artifact, artifactStorage: ArtifactStorage
) {
    val targets = artifactStorage.getArtifactTargets(artifact = artifact)
        .filter { it.artifactId.isKmpVariationOf(artifact) }
    if (targets.isEmpty()) {
        println("No Kotlin Multiplatform Targets found for '$artifact'")
        return
    } else {
        println("All Kotlin Multiplatform Targets for '$artifact': ")
        targets.sorted().forEach { println("* $it") }
    }
}

private fun processAvailableVersions(
    artifact: Artifact, artifactStorage: ArtifactStorage
) {
    val versionsMeta = artifactStorage.getArtifactVersions(artifact = artifact)
    if (versionsMeta.versions.isEmpty()) {
        println("No versions found")
        return
    } else {
        println("All Versions for '$artifact': ")
        versionsMeta.versions.sortedDescending().forEach { version ->
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
    indexer.index().flowOn(Dispatchers.IO).throttleLatest(1.seconds).collect { progress ->
        progressRenderer.render(progress)
    }

}

fun <T> Flow<T>.throttleLatest(duration: Duration): Flow<T> = this.conflate().transform {
    emit(it)
    delay(duration)
}
