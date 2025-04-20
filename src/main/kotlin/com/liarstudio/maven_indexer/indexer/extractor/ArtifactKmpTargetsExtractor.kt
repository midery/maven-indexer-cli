package com.liarstudio.maven_indexer.indexer.extractor

import com.liarstudio.maven_indexer.MAVEN_CENTRAL_REPO_URL
import com.liarstudio.maven_indexer.models.Artifact
import com.liarstudio.maven_indexer.groupIdUrlPath

class ArtifactKmpTargetsExtractor(
    private val htmlPageLinkExtractor: HtmlPageLinkExtractor,
    private val host: String = MAVEN_CENTRAL_REPO_URL,
) {

    /**
     * Extracts KMP targets from an artifact.
     *
     * **Algorithm**: go through each url in artifact group, and check if it is a valid kmp variant.
     */
    suspend fun getKmpVariants(artifact: Artifact): List<Artifact> {
        return htmlPageLinkExtractor.invoke(artifact.getArtifactGroupUrl())
            .asSequence()
            .map { it.trim('/') }
            .filter { it.isKmpVariationOf(artifact) }
            .map { link -> Artifact(artifact.groupId, link) }
            .toList()
    }

    private fun Artifact.getArtifactGroupUrl() = "$host$groupIdUrlPath"

    companion object {

        /**
         * Defines whether the artifact is a KMP variant of another artifact or not.
         *
         * Current implementation is defined by checking if an artifact is suffixed by predefined KMP-related
         * platform targets.
         *
         * TODO: Current implementation is not scalable and may be easily broken,
         *  as there's no required naming convention for KMP targets.
         *  In order to correctly parse available kmp targets, we should parse .module file for each version of an artifact.
         *  Example: https://repo.maven.apache.org/maven2/io/ktor/ktor-io/3.0.1/ktor-io-3.0.1.module
         */
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