package com.liarstudio.maven_indexer.parser

import com.liarstudio.maven_indexer.indexer.parser.comparator.VersionComparator
import com.liarstudio.maven_indexer.indexer.parser.XmlMetadataParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class XmlMetadataParserTest {

    private val versionComparator = VersionComparator()
    private val parser = XmlMetadataParser(versionComparator = versionComparator)

    @Test
    fun `GIVEN valid metadata xml with latest and release WHEN parsed THEN returns correct metadata`() {
        val xml = """
            <metadata>
                <versioning>
                    <latest>2.1.0</latest>
                    <release>2.0.0</release>
                    <versions>
                        <version>1.0.0</version>
                        <version>2.0.0</version>
                        <version>2.1.0</version>
                    </versions>
                </versioning>
            </metadata>
        """.trimIndent()

        val result = parser.parse(xml)

        assertEquals(listOf("1.0.0", "2.0.0", "2.1.0"), result.versions)
        assertEquals("2.1.0", result.latestVersion)
        assertEquals("2.0.0", result.releaseVersion)
    }

    @Test
    fun `GIVEN metadata xml without latest and release WHEN parsed THEN calculates latest from versions`() {
        val xml = """
            <metadata>
                <versioning>
                    <versions>
                        <version>0.9.0</version>
                        <version>1.0.0</version>
                        <version>1.1.0</version>
                    </versions>
                </versioning>
            </metadata>
        """.trimIndent()

        val result = parser.parse(xml)

        assertEquals(listOf("0.9.0", "1.0.0", "1.1.0"), result.versions)
        assertEquals("1.1.0", result.latestVersion)
        assertEquals("1.1.0", result.releaseVersion)
    }

    @Test
    fun `GIVEN empty versions list WHEN parsed THEN returns empty metadata`() {
        val xml = """
            <metadata>
                <versioning>
                    <versions>
                    </versions>
                </versioning>
            </metadata>
        """.trimIndent()

        val result = parser.parse(xml)

        assertEquals(emptyList<String>(), result.versions)
        assertEquals(null, result.latestVersion)
        assertEquals(null, result.releaseVersion)
    }

    @Test
    fun `GIVEN malformed xml WHEN parsed THEN throws exception`() {
        val xml = "<metadata><versioning><versions><version>1.0.0</version>" // no closing tags

        val exception = assertThrows<Exception> { parser.parse(xml) }

        assertEquals("XML document structures must start and end within the same entity.", exception.message)
    }
}
