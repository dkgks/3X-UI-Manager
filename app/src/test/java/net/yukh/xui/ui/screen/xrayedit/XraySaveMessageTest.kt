package net.yukh.xui.ui.screen.xrayedit

import org.junit.Assert.assertEquals
import org.junit.Test

/** Panel v3.3.1 made `xray/update` apply the template itself; older panels only store it. */
class XraySaveMessageTest {

    @Test
    fun `panels that apply on save are told it is applied`() {
        assertEquals(XRAY_SAVED_APPLIED, xraySaveMessage("3.3.1"))
        assertEquals(XRAY_SAVED_APPLIED, xraySaveMessage("v3.10.0"))
    }

    @Test
    fun `older or unknown panels still ask for a restart`() {
        assertEquals(XRAY_SAVED_RESTART, xraySaveMessage("3.3.0"))
        assertEquals(XRAY_SAVED_RESTART, xraySaveMessage("3.2.8"))
        assertEquals(XRAY_SAVED_RESTART, xraySaveMessage(""))
    }
}
