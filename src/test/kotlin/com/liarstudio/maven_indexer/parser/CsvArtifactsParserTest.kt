package com.liarstudio.maven_indexer.parser

import com.liarstudio.maven_indexer.indexer.parser.CsvArtifactsParser
import com.liarstudio.maven_indexer.models.Artifact
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CsvArtifactsParserTest {

    private val parser = CsvArtifactsParser()

    @Test
    fun GIVEN_empty_file_WHEN_parse_THEN_throw_error(
        @TempDir tempDir: File
    ) {

        val csvFile = File(tempDir, "empty.csv")
        csvFile.createNewFile()

        val error = assertThrows<NullPointerException> {
            parser(csvFile)
        }

        assertEquals("Cannot read the array length because \"headers\" is null", error.message)
    }

    @Test
    fun GIVEN_no_artifacts_WHEN_parse_THEN_return_empty_list(
        @TempDir tempDir: File
    ) {

        val csvFile = File(tempDir, "empty.csv")
        csvFile.createNewFile()
        csvFile.writeText("namespace,name")

        val artifacts = parser(csvFile)

        assertEquals(emptyList<Artifact>(), artifacts)
    }

    @Test
    fun GIVEN_valid_file_WHEN_parse_THEN_return_artifacts(
        @TempDir tempDir: File
    ) {

        val csvFile = File(tempDir, "empty.csv")
        csvFile.createNewFile()
        csvFile.writeText("namespace,name\nio.ktor,ktor-server\nio.ktor,ktor-client-cio")

        val artifacts = parser(csvFile)

        assertEquals(
            listOf(
                Artifact(groupId = "io.ktor", artifactId = "ktor-server"),
                Artifact(groupId = "io.ktor", artifactId = "ktor-client-cio"),
            ), artifacts
        )
    }

    @Test
    fun GIVEN_invalid_file_WHEN_parse_THEN_throws_error(
        @TempDir tempDir: File
    ) {

        val csvFile = File(tempDir, "empty.csv")
        csvFile.createNewFile()
        csvFile.writeText("random,column\nio.ktor,ktor-server\nio.ktor,ktor-client-cio")

        val error = assertThrows<IllegalStateException> {
            parser(csvFile)
        }

        assertEquals(
            "Invalid file format: $csvFile. It should have headers named  'namespace' and 'name'.",
            error.message
        )

    }
}