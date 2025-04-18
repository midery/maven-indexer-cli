package com.liarstudio.maven_indexer.indexer.kmp

import com.liarstudio.maven_indexer.models.Artifact
import com.liarstudio.maven_indexer.parser.WebPageLinkUrlParser

class ArtifactKmpVariantsExtractor(
    private val webPageLinkUrlParser: WebPageLinkUrlParser,
) {

    suspend fun getKmpVariants(artifact: Artifact): List<Artifact> {
        return webPageLinkUrlParser.parse(
            "https://repo1.maven.org/maven2/${artifact.groupId.replace('.', '/')}",
        )
            .mapNotNull { link ->
                if (!link.startsWith(artifact.artifactId)) null else {
                    Artifact(artifact.groupId, link.trim('/'))
                }
            }
    }
}