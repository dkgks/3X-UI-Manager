package net.yukh.xui.ui.screen.inbounds

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import net.yukh.xui.data.api.dto.InboundModel
import org.junit.Assert.assertEquals
import org.junit.Test

class InboundPayloadTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun `an edit sends back the fields the panel would otherwise reset`() {
        val loaded = json.decodeFromString(
            InboundModel.serializer(),
            """{"id":7,"remark":"r","port":443,"protocol":"vless","tag":"my-in","subSortIndex":5,
               "excludeFromSub":true,"trafficReset":"monthly","trafficResetDay":15,"clientStats":[]}""",
        )
        val state = InboundEditorState(isNew = false, id = 7, remark = "renamed", port = "8443", protocol = "vless", loaded = loaded)

        val body = json.encodeToJsonElement(InboundModel.serializer(), state.toInboundModel(JsonObject(emptyMap()))).jsonObject

        assertEquals("\"my-in\"", body["tag"].toString())
        assertEquals("5", body["subSortIndex"].toString())
        assertEquals("true", body["excludeFromSub"].toString())
        assertEquals("15", body["trafficResetDay"].toString())
        assertEquals("\"renamed\"", body["remark"].toString())
        assertEquals("8443", body["port"].toString())
    }

    @Test
    fun `a new inbound sends the panel's defaults and an empty tag to generate`() {
        val body = InboundEditorState(isNew = true, port = "443").toInboundModel(JsonObject(emptyMap()))
        assertEquals("", body.tag)
        assertEquals(1, body.subSortIndex)
        assertEquals(false, body.excludeFromSub)
        assertEquals(1, body.trafficResetDay)
    }
}
