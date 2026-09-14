package net.yukh.xui.data.repo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelVersionTest {

    @Test
    fun `equal and newer versions pass`() {
        assertTrue(panelVersionAtLeast("3.8.0", 3, 8, 0))
        assertTrue(panelVersionAtLeast("3.8.1", 3, 8, 0))
        assertTrue(panelVersionAtLeast("3.10.0", 3, 8, 0))
        assertTrue(panelVersionAtLeast("4.0.0", 3, 8, 0))
    }

    @Test
    fun `older versions fail, including a higher patch of an older minor`() {
        assertFalse(panelVersionAtLeast("3.7.9", 3, 8, 0))
        assertFalse(panelVersionAtLeast("2.9.9", 3, 8, 0))
        assertFalse(panelVersionAtLeast("3.7.0", 3, 8, 0))
    }

    @Test
    fun `prefix, pre-release suffix and missing patch are understood`() {
        assertTrue(panelVersionAtLeast("v3.8.0", 3, 8, 0))
        assertTrue(panelVersionAtLeast("3.8.0-beta1", 3, 8, 0))
        assertTrue(panelVersionAtLeast("3.8", 3, 8, 0))
        assertFalse(panelVersionAtLeast("3.7", 3, 8, 0))
    }

    @Test
    fun `unknown versions are treated as unsupported`() {
        assertFalse(panelVersionAtLeast("", 3, 8, 0))
        assertFalse(panelVersionAtLeast("dev", 3, 8, 0))
        assertFalse(supportsPanel380("unknown"))
    }
}
