package com.liarstudio.maven_indexer.extractor

import com.liarstudio.maven_indexer.data.network.NetworkClient
import com.liarstudio.maven_indexer.indexer.extractor.MavenMetadataExtractor
import com.liarstudio.maven_indexer.indexer.parser.XmlMetadataParser
import com.liarstudio.maven_indexer.models.Artifact
import com.liarstudio.maven_indexer.models.ArtifactVersionMetadata
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking

class MavenMetadataExtractorTest {

    private val networkClient = Mockito.mock<NetworkClient>()
    private val xmlParser = Mockito.mock<XmlMetadataParser>()
    private val mavenMetadataExtractor = MavenMetadataExtractor(
        networkClient = networkClient,
        xmlParser = xmlParser,
        host = "test://maven/",
    )

    @Test
    fun GIVEN_artifact_WHEN_invoke_THEN_url_is_correct() = runTest {
        val artifact = Artifact("foo.bar", "xyz-abc")
        mavenMetadataExtractor.invoke(artifact)

        Mockito.verify(networkClient).getBody("test://maven/foo/bar/xyz-abc/maven-metadata.xml")
    }

    @Test
    fun GIVEN_artifact_WHEN_invoke_THEN_xml_passed_to_parser() = runTest {
        val artifact = Artifact("foo.bar", "xyz-abc")
        wheneverBlocking { networkClient.getBody("test://maven/foo/bar/xyz-abc/maven-metadata.xml") }
            .thenReturn("test xml")

        mavenMetadataExtractor.invoke(artifact)

        Mockito.verify(xmlParser).parse("test xml")
    }

    @Test
    fun GIVEN_artifact_WHEN_invoke_THEN_return_value_from_parser() = runTest {
        val artifact = Artifact("foo.bar", "xyz-abc")
        val expectedMetadata = ArtifactVersionMetadata(
            versions = listOf("1.2.3", "1.2.2"),
            latestVersion = null,
            releaseVersion = null
        )

        whenever(xmlParser.parse(anyOrNull())).thenReturn(expectedMetadata)

        Assertions.assertEquals(expectedMetadata, mavenMetadataExtractor.invoke(artifact))
    }
}