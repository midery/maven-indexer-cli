package com.liarstudio.maven_indexer.extractor

import com.liarstudio.maven_indexer.indexer.extractor.ArtifactKmpTargetsExtractor
import com.liarstudio.maven_indexer.indexer.extractor.HtmlPageLinkExtractor
import com.liarstudio.maven_indexer.models.Artifact
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class ArtifactKmpTargetsExtractorTest {

    private val htmlExtractor: HtmlPageLinkExtractor = mock()
    private val extractor = ArtifactKmpTargetsExtractor(
        htmlPageLinkExtractor = htmlExtractor,
        host = "test://maven/"
    )

    @Test
    fun `GIVEN list of links with KMP targets WHEN getKmpVariants called THEN returns only valid KMP variants`() =
        runTest {
            // GIVEN
            val artifact = Artifact(groupId = "com.example", artifactId = "library")
            val links = listOf(
                "../",              // should be ignored
                "library/",         // same as base
                "library-jvm/",     // valid KMP
                "library-js/",      // valid KMP
                "library-wasm/",    // valid KMP
                "library-native/",  // valid KMP
                "some-other-lib/"   // unrelated
            )

            wheneverBlocking { htmlExtractor.invoke("test://maven/com/example") }
                .thenReturn(links)

            // WHEN
            val result = extractor.getKmpVariants(artifact)

            // THEN
            assertEquals(
                listOf(
                    Artifact("com.example", "library-jvm"),
                    Artifact("com.example", "library-js"),
                    Artifact("com.example", "library-wasm"),
                    Artifact("com.example", "library-native"),
                ),
                result
            )
        }

    @Test
    fun `GIVEN no KMP variants in links WHEN getKmpVariants called THEN returns empty list`() = runTest {
        val artifact = Artifact("org.test", "base")
        val links = listOf("otherlib/", "base/", "unrelated/")

        wheneverBlocking { htmlExtractor.invoke("test://maven/org/test") }.thenReturn(links)

        val result = extractor.getKmpVariants(artifact)

        assertEquals(emptyList<Artifact>(), result)
    }

    @Test
    fun `GIVEN mixed-case KMP targets WHEN getKmpVariants called THEN matching is case-insensitive`() = runTest {
        val artifact = Artifact("com.test", "mylib")
        val links = listOf("mylib-JVM/", "mylib-IOSX64/", "mylib-js/")

        whenever(htmlExtractor.invoke(any())).thenReturn(links)

        val result = extractor.getKmpVariants(artifact)

        assertEquals(
            listOf(
                Artifact("com.test", "mylib-JVM"),
                Artifact("com.test", "mylib-IOSX64"),
                Artifact("com.test", "mylib-js"),
            ),
            result
        )
    }
}
