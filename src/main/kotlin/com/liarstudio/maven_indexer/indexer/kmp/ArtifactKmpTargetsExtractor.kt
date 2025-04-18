package com.liarstudio.maven_indexer.indexer.kmp

import com.liarstudio.maven_indexer.models.Artifact
import com.liarstudio.maven_indexer.parser.WebPageLinkUrlParser

class ArtifactKmpTargetsExtractor(
    private val webPageLinkUrlParser: WebPageLinkUrlParser,
) {

    suspend fun getKmpVariants(artifact: Artifact): List<Artifact> {
        return webPageLinkUrlParser.parse(
            "https://repo1.maven.org/maven2/${artifact.groupId.replace('.', '/')}",
        )
            .asSequence()
            .map { it.trim('/') }
            .filter { it.isKmpVariationOf(artifact) }
            .map { link -> Artifact(artifact.groupId, link) }
            .toList()
    }

    companion object {

        fun String.isKmpVariationOf(originalArtifact: Artifact): Boolean {
            return KMP_TARGETS.any { suffix ->
                this.equals(
                    "${originalArtifact.artifactId}-$suffix",
                    ignoreCase = true
                )
            }
        }

        private val KMP_TARGETS = buildSet {
            val architectures = listOf(
                "arm64", "x64", "arm32", "arm64", "arm32hfp", "x86", "mips32", "mipsel32"
            )

            add("native")
            add("common")
            add("jvm")
            add("android")
            add("js")
            add("jsir")
            add("wasm")
            add("wasmjs")
            add("wasm32")
            for (arch in architectures) {
                add("ios$arch")
                add("iossimulator$arch")
                add("androidnative$arch")
                add("linux$arch")
                add("mingw$arch")
                add("watchos$arch")
                add("macos$arch")
                add("tvos$arch")
                add("tvossimulator$arch")
                add("watchossimulator$arch")
                add("watchosdevice$arch")
                add("windows$arch")
            }
        }
    }
}