package com.liarstudio.maven_indexer

import com.liarstudio.maven_indexer.cli.ApplicationRunner
import com.liarstudio.maven_indexer.cli.logger.LogLevel
import kotlinx.cli.*

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

    ApplicationRunner()
        .run(
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