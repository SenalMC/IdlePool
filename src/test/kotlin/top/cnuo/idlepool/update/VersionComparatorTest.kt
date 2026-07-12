package top.cnuo.idlepool.update

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VersionComparatorTest {
    @Test fun `compares releases and prereleases`() {
        assertTrue(VersionComparator.compare("1.1.0", "1.0.0") > 0)
        assertEquals(0, VersionComparator.compare("v1.1.0", "1.1.0"))
        assertTrue(VersionComparator.compare("1.1.0-rc.10", "1.1.0-rc.9") > 0)
        assertTrue(VersionComparator.compare("1.1.0", "1.1.0-rc.99") > 0)
    }
}
