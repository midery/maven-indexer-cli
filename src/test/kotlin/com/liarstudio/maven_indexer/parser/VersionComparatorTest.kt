import com.liarstudio.maven_indexer.indexer.parser.comparator.VersionComparator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VersionComparatorTest {

    private val comparator = VersionComparator()

    @Test
    fun `GIVEN valid semver versions WHEN compared THEN uses semver order`() {
        val result = comparator.compare("1.0.0", "2.0.0")
        assertEquals(-1, result)
    }

    @Test
    fun `GIVEN equal semver versions WHEN compared THEN returns zero`() {
        val result = comparator.compare("1.2.3", "1.2.3")
        assertEquals(0, result)
    }

    @Test
    fun `GIVEN prerelease semver versions WHEN compared THEN handles correctly`() {
        val result = comparator.compare("1.2.3-alpha", "1.2.3")
        assertEquals(-1, result) // prerelease < release
    }

    @Test
    fun `GIVEN invalid semver strings WHEN compared THEN falls back to string comparison`() {
        val result = comparator.compare("abc", "bcd")
        assertEquals("abc".compareTo("bcd"), result)
    }

    @Test
    fun `GIVEN one valid and one invalid version WHEN compared THEN falls back to string comparison`() {
        val result = comparator.compare("1.0.0", "not-a-version")
        assertEquals("1.0.0".compareTo("not-a-version"), result)
    }

    @Test
    fun `GIVEN numeric versions without patch WHEN compared THEN compares semantically`() {
        val result = comparator.compare("1.0", "1.0.1")
        assertEquals(-1, result)
    }

    @Test
    fun `GIVEN version with build metadata WHEN compared THEN ignores metadata`() {
        val result = comparator.compare("1.0.0+build.1", "1.0.0+build.2")
        assertEquals(0, result) // build metadata does not affect precedence
    }
}
