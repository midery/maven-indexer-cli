package com.liarstudio.maven_indexer

import com.liarstudio.maven_indexer.cli.CliCommandsProcessor
import com.liarstudio.maven_indexer.cli.logger.LogLevel
import kotlinx.cli.*

fun main(args: Array<String>) {
    val parser = ArgParser("maven-indexer")

    val index by parser.option(
        ArgType.Boolean, shortName = "i", description = "Index all dependencies from Maven Central." +
                "This method will crawl through each artifact in Maven Central and get its meta-information." +
                "\n\tEstimated time: ~40 minutes."
    )

    val indexGroup by parser.option(
        ArgType.String,
        shortName = "ig",
        description = "Index all dependencies for particular groupId from Maven Central.\n\t" +
                "Example input: io.ktor\n\t" +
                "This method can be used if you don't wish to wait until the whole maven central finishes indexing," +
                "and should work significantly faster then `index`."
    )

    val indexArtifact by parser.option(
        ArgType.String, shortName = "ia", description = "Index single artifact. Input format: group:artifactId"
    )
    val indexFromCsv by parser.option(
        ArgType.String,
        shortName = "ic",
        description = "Index from CSV file. CSV should be specified in input format with two columns: 'namespace' for artifact's groupId and 'name' for artifactId. \n\t" +
                "This program will try to index all the artifacts from the CSV file, as well as their KMP targets.\n\tFor example, if you add a row with 'ktor-network' library, " +
                "it will index all the possible kmp targets: \n\t* ktor-network-js, \n\t* ktor-network-jvm, \n\t* ktor-network-iosx64, etc."
    )
    val refresh by parser.option(
        ArgType.Boolean,
        shortName = "r",
        description = "Refreshes already indexed artifact versions. This action should be faster than refreshing whole Maven Central, and can be performed periodically." +
                "\n\tEstimated time: ~15 minutes."
    )

    val search by parser.option(
        ArgType.String, shortName = "s", description = "Search artifact by name (group or artifactId)" +
                "\n\tYou should `index` the project before running search, as otherwise local storage will be empty." +
                "\n\tExample input:" +
                "\n\t* `kotlin`" +
                "\n\t* `mockito android`" +
                "\n\t* `io.ktor:ktor-network`"
    )
    val targets by parser.option(
        ArgType.String,
        shortName = "t",
        description = "Find single artifact's available Kotlin Multiplatform Targets." +
                "\n\tYou should `index` the project before running search, as otherwise local storage will be empty." +
                "\n\tInput format: group:artifactId"
    )
    val versions by parser.option(
        ArgType.String,
        shortName = "v",
        description = "Find single artifact's available Versions." +
                "\n\tYou should `index` the project before running search, as otherwise local storage will be empty." +
                "\n\tInput format: group:artifactId"
    )

    val logLevel by parser.option(
        ArgType.Choice<LogLevel>(), shortName = "l", description = "Specifies desired log  level. "
    )
        .default(LogLevel.warn)

    parser.parse(args)

    CliCommandsProcessor()
        .process(
            index = index,
            indexGroup = indexGroup,
            indexFromCsv = indexFromCsv,
            indexArtifact = indexArtifact,
            search = search,
            refresh = refresh,
            targets = targets,
            versions = versions,
            logLevel = logLevel,
        )
}