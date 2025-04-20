package com.liarstudio.maven_indexer.cli

import com.liarstudio.maven_indexer.cli.logger.LogLevel
import com.liarstudio.maven_indexer.cli.parser.ArtifactParamParser
import com.liarstudio.maven_indexer.cli.printer.ProgressRenderer
import com.liarstudio.maven_indexer.cli.processor.IndexArtifactsCommandProcessor
import com.liarstudio.maven_indexer.cli.processor.IndexSingleArtifactCommandProcessor
import com.liarstudio.maven_indexer.cli.processor.SearchArtifactsCommandProcessor
import com.liarstudio.maven_indexer.cli.processor.SearchTargetsCommandProcessor
import com.liarstudio.maven_indexer.cli.processor.SearchVersionsCommandProcessor
import com.liarstudio.maven_indexer.data.network.NetworkClient
import com.liarstudio.maven_indexer.data.storage.ArtifactStorage
import com.liarstudio.maven_indexer.indexer.CsvArtifactIndexer
import com.liarstudio.maven_indexer.indexer.FullMavenArtifactIndexer
import com.liarstudio.maven_indexer.indexer.SingleArtifactIndexer
import com.liarstudio.maven_indexer.indexer.VersionOnlyArtifactIndexer
import com.liarstudio.maven_indexer.indexer.parser.comparator.VersionComparator
import com.liarstudio.maven_indexer.indexer.extractor.ArtifactKmpTargetsExtractor
import com.liarstudio.maven_indexer.indexer.extractor.HtmlPageLinkExtractor
import com.liarstudio.maven_indexer.indexer.extractor.MavenMetadataExtractor
import com.liarstudio.maven_indexer.indexer.parser.CsvArtifactsParser
import com.liarstudio.maven_indexer.indexer.parser.XmlMetadataParser
import kotlinx.coroutines.runBlocking
import java.io.File

class CliApplication {

    fun run(
        index: Boolean?,
        indexGroup: String?,
        indexArtifact: String?,
        indexFromCsv: String?,
        search: String?,
        refresh: Boolean?,
        targets: String?,
        versions: String?,
        logLevel: LogLevel,
    ) {

        initializeLogging(logLevel)
        val progressRenderer = ProgressRenderer()
        val networkClient = NetworkClient()
        val artifactStorage = ArtifactStorage()
        val parseArtifact = ArtifactParamParser()
        val versionComparator = VersionComparator()
        val xmlParser = XmlMetadataParser(versionComparator)

        artifactStorage.initialize()

        val singleArtifactIndexer =
            SingleArtifactIndexer(MavenMetadataExtractor(networkClient, xmlParser), artifactStorage)

        val processArtifactsIndexing = IndexArtifactsCommandProcessor()
        val processSingleArtifactIndexing = IndexSingleArtifactCommandProcessor()
        val processArtifactsSearch = SearchArtifactsCommandProcessor()
        val processTargetsSearch = SearchTargetsCommandProcessor()
        val processVersionsSearch = SearchVersionsCommandProcessor()

        runBlocking {
            when {
                index == true -> processArtifactsIndexing(
                    indexer = FullMavenArtifactIndexer(
                        indexer = singleArtifactIndexer,
                        htmlPageLinkExtractor = HtmlPageLinkExtractor(networkClient),
                    ),
                    progressRenderer = progressRenderer
                )

                indexGroup != null -> processArtifactsIndexing(
                    indexer = FullMavenArtifactIndexer(
                        indexer = singleArtifactIndexer,
                        htmlPageLinkExtractor = HtmlPageLinkExtractor(networkClient),
                        additionalPath = indexGroup.replace('.', '/') + '/'
                    ),
                    progressRenderer = progressRenderer
                )

                indexArtifact != null ->
                    processSingleArtifactIndexing(parseArtifact(indexArtifact), singleArtifactIndexer)

                indexFromCsv != null -> processArtifactsIndexing(
                    indexer = CsvArtifactIndexer(
                        csvFile = File(indexFromCsv),
                        indexer = singleArtifactIndexer,
                        csvParser = CsvArtifactsParser(),
                        kmpVariantsExtractor = ArtifactKmpTargetsExtractor(HtmlPageLinkExtractor(networkClient)),
                    ), progressRenderer = progressRenderer
                )

                search != null -> processArtifactsSearch(search, artifactStorage)
                refresh != null -> processArtifactsIndexing(
                    indexer = VersionOnlyArtifactIndexer(
                        artifactStorage, singleArtifactIndexer
                    ),
                    progressRenderer
                )

                targets != null -> processTargetsSearch(parseArtifact(targets), artifactStorage)
                versions != null -> processVersionsSearch(parseArtifact(versions), artifactStorage)
                else -> println("Use --help to see options.")
            }
        }
    }

    private fun initializeLogging(logLevel: LogLevel) {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", logLevel.name)
    }
}