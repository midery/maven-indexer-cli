package com.liarstudio.maven_indexer.indexer.kmp

import com.liarstudio.maven_indexer.MAVEN_CENTRAL_REPO_URL
import com.liarstudio.maven_indexer.models.Artifact
import com.liarstudio.maven_indexer.parser.WebPageLinkUrlParser
import com.liarstudio.maven_indexer.parser.groupIdUrlPath

class ArtifactKmpTargetsExtractor(
    private val webPageLinkUrlParser: WebPageLinkUrlParser,
    private val host: String = MAVEN_CENTRAL_REPO_URL,
) {

    suspend fun getKmpVariants(artifact: Artifact): List<Artifact> {
        return webPageLinkUrlParser.parse(artifact.getArtifactGroupUrl())
            .asSequence()
            .map { it.trim('/') }
            .filter { it.isKmpVariationOf(artifact) }
            .map { link -> Artifact(artifact.groupId, link) }
            .toList()
    }

    private fun Artifact.getArtifactGroupUrl() = "$host$groupIdUrlPath"

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