package net.yukh.xui.shared.api

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PanelVersionTest {

    @Test
    fun equalAndNewerVersionsPass() {
        assertTrue(panelVersionAtLeast("3.8.0", 3, 8, 0))
        assertTrue(panelVersionAtLeast("3.8.1", 3, 8, 0))
        assertTrue(panelVersionAtLeast("3.10.0", 3, 8, 0))
        assertTrue(panelVersionAtLeast("4.0.0", 3, 8, 0))
    }

    @Test
    fun olderVersionsFailIncludingAHigherPatchOfAnOlderMinor() {
        assertFalse(panelVersionAtLeast("3.7.9", 3, 8, 0))
        assertFalse(panelVersionAtLeast("2.9.9", 3, 8, 0))
        assertFalse(panelVersionAtLeast("3.7.0", 3, 8, 0))
    }

    @Test
    fun prefixPreReleaseSuffixAndMissingPatchAreUnderstood() {
        assertTrue(panelVersionAtLeast("v3.8.0", 3, 8, 0))
        assertTrue(panelVersionAtLeast(" 3.8.0-beta1 ", 3, 8, 0))
        assertTrue(panelVersionAtLeast("3.8", 3, 8, 0))
        assertFalse(panelVersionAtLeast("3.7", 3, 8, 0))
    }

    @Test
    fun unknownVersionsAreTreatedAsUnsupported() {
        assertFalse(panelVersionAtLeast("", 3, 8, 0))
        assertFalse(panelVersionAtLeast("dev", 3, 8, 0))
        // Too large for an Int: unsupported rather than a crash.
        assertFalse(panelVersionAtLeast("99999999999.0.0", 3, 8, 0))
        assertFalse(supportsPanel380("unknown"))
        assertTrue(supportsPanel380("3.8.0"))
    }
}
