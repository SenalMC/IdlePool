package top.cnuo.idlepool.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionComparatorTest {
    @Test
    void comparesReleaseNumbers() {
        assertTrue(VersionComparator.compare("1.0.1", "1.0.0") > 0);
        assertTrue(VersionComparator.compare("1.10.0", "1.9.9") > 0);
        assertEquals(0, VersionComparator.compare("v1.0.0", "1.0.0"));
    }

    @Test
    void comparesReleaseCandidatesNumerically() {
        assertTrue(VersionComparator.compare("1.0.0-rc.4", "1.0.0-rc.3") > 0);
        assertTrue(VersionComparator.compare("1.0.0-rc.10", "1.0.0-rc.9") > 0);
    }

    @Test
    void stableReleaseIsNewerThanPrerelease() {
        assertTrue(VersionComparator.compare("1.0.0", "1.0.0-rc.99") > 0);
    }
}
