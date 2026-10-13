package net.yukh.xui.data.api.dto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The JSON subscription URL follows the panel's GetDefaultSettings (subJsonURI or base + subJsonPath). */
class JsonSubUrlTest {
    private val on = PanelSettings(subEnable = true, subJsonEnable = true, subPort = 2096, subJsonPath = "/json/")

    @Test
    fun builtFromSubServerBaseAndJsonPath() {
        assertEquals("http://host:2096/json/abc", on.jsonSubscriptionUrl("host", "abc"))
        assertEquals("https://sub.example/j/abc", on.copy(subJsonURI = "https://sub.example/j").jsonSubscriptionUrl("host", "abc"))
    }

    @Test
    fun absentWhileEitherSwitchIsOff() {
        assertNull(on.copy(subJsonEnable = false).jsonSubscriptionUrl("host", "abc"))
        assertNull(on.copy(subEnable = false).jsonSubscriptionUrl("host", "abc"))
    }
}
