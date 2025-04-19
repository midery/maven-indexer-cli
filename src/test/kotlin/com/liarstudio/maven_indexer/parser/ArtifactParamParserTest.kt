package com.liarstudio.maven_indexer.parser

import com.liarstudio.maven_indexer.cli.parser.ArtifactParamParser
import com.liarstudio.maven_indexer.models.Artifact
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ArtifactParamParserTest {

    private val parser = ArtifactParamParser()

    @Test
    fun GIVEN_invalid_param_WHEN_invoke_THEN_throws_error() {
        val error = assertThrows<IllegalStateException> { parser("invalid_input") }

        assertEquals("Artifact should be specified in a format: foo.bar:xyz or foo.bar.xyz:1.2.3", error.message)
    }

    @Test
    fun GIVEN_no_artifact_id_WHEN_invoke_THEN_throw_error() {
        val error = assertThrows<IllegalStateException> { parser("foo.bar") }

        assertEquals("Artifact should be specified in a format: foo.bar:xyz or foo.bar.xyz:1.2.3", error.message)
    }

    @Test
    fun GIVEN_artifact_WHEN_invoke_THEN_return_artifact() {
        val artifact = parser("foo.bar:xyz")

        assertEquals(Artifact("foo.bar", "xyz"), artifact)
    }
    @Test
    fun GIVEN_artifact_with_version_WHEN_invoke_THEN_return_artifact() {
        val artifact = parser("foo.bar:xyz:1.2.3")

        assertEquals(Artifact("foo.bar", "xyz"), artifact)
    }
}