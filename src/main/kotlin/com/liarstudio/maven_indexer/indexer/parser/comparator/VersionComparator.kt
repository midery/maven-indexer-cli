package com.liarstudio.maven_indexer.indexer.parser.comparator

import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.VersionFormatException

/**
 * Compares semver artifact versions. If the artifact is not a valid version according to semver,
 * will use a simple string comparison.
 */
class VersionComparator : Comparator<String> {

    override fun compare(o1: String, o2: String): Int {
        return try {
            val version1 = Version.parse(o1, false)
            val version2 = Version.parse(o2, false)
            version1.compareTo(version2)
        } catch (_: VersionFormatException) {
            return o1.compareTo(o2)
        }
    }
}