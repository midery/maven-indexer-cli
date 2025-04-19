package com.liarstudio.maven_indexer.extractor

import com.liarstudio.maven_indexer.data.network.NetworkClient
import com.liarstudio.maven_indexer.indexer.extractor.HtmlPageLinkExtractor
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

class HtmlPageLinkExtractorTest {

    private val mockClient = mock<NetworkClient>()
    private val extractor = HtmlPageLinkExtractor(mockClient)


    @Test
    fun `GIVEN list of links WHEN invoke THEN return links excluding parent`() = runTest {
        val html = """
            <html>
              <body>
                <a href="../">../</a>
                <a href="foo/">foo/</a>
                <a href="bar/">bar/</a>
                <a href="baz/">baz/</a>
              </body>
            </html>
        """.trimIndent()

        whenever(mockClient.getBody("https://test.com/")).thenReturn(html)

        val result = extractor.invoke("https://test.com/")

        assertEquals(listOf("foo/", "bar/", "baz/"), result)
        verify(mockClient).getBody("https://test.com/")
    }

    @Test
    fun `GIVEN no links WHEN invoke THEN should return empty list`() = runTest {
        val html = "<html><body><p>No links here</p></body></html>"

        whenever(mockClient.getBody("https://empty.com/")).thenReturn(html)

        val result = extractor.invoke("https://empty.com/")

        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `GIVEN non-links html tags WHEN invoke THEN return empty list`() = runTest {
        val html = """
            <html>
              <body>
                <a name="anchor1">Anchor</a>
                <div href="fake/">Fake</div>
              </body>
            </html>
        """.trimIndent()

        whenever(mockClient.getBody(any())).thenReturn(html)

        val result = extractor.invoke("https://non-links.com/")

        assertEquals(emptyList<String>(), result)
    }
}
