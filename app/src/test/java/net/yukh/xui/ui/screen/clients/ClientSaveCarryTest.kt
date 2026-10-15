package net.yukh.xui.ui.screen.clients

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.yukh.xui.data.api.dto.Client
import net.yukh.xui.data.api.dto.ClientModel
import net.yukh.xui.data.api.dto.InboundSlim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The panel's client update overwrites resetWeekday and reverse from the body, so an
 * edit that leaves them out turned weekly renewal off and dropped the VLESS reverse tunnel.
 */
class ClientSaveCarryTest {
    // Same settings as NetworkModule.provideJson().
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
        encodeDefaults = true
    }

    private val listed = json.decodeFromString(
        Client.serializer(),
        """{"id":4,"email":"rev","uuid":"u-1","subId":"s1","resetWeekday":3,
           "reverse":{"tag":"portal"},"inboundIds":[1]}""",
    )

    private fun editorFor(client: Client, vararg inbounds: InboundSlim) = ClientEditorState(
        isNew = false,
        source = client,
        email = client.email,
        resetDay = client.resetDay.toString(),
        reset = client.reset.toString(),
        selectedInboundIds = client.inboundIds.toSet(),
        availableInbounds = inbounds.toList(),
    )

    private fun body(model: ClientModel) = json.encodeToJsonElement(ClientModel.serializer(), model).jsonObject

    @Test
    fun editKeepsWeeklyRenewalAndReverse() {
        val sent = body(editorFor(listed).copy(comment = "changed").toClientModel())

        assertEquals(3, sent["resetWeekday"]!!.jsonPrimitive.int)
        assertEquals("portal", sent["reverse"]!!.jsonObject["tag"]!!.jsonPrimitive.content)
        assertEquals("changed", sent["comment"]!!.jsonPrimitive.content)
    }

    @Test
    fun settingARenewalDayTurnsWeeklyRenewalOff() {
        // The panel refuses resetWeekday next to reset or resetDay.
        val byDay = editorFor(listed).copy(resetDay = "15").toClientModel()
        val byPeriod = editorFor(listed).copy(reset = "30").toClientModel()

        assertEquals(0, byDay.resetWeekday)
        assertEquals(0, byPeriod.resetWeekday)
    }

    @Test
    fun clientWithoutReverseSendsNoReverse() {
        val plain = json.decodeFromString(Client.serializer(), """{"email":"p","uuid":"u","reverse":null}""")

        assertFalse(body(editorFor(plain).toClientModel()).containsKey("reverse"))
    }

    @Test
    fun legacyStringReverseIsSentAsObject() {
        val legacy = Client(email = "l", reverse = kotlinx.serialization.json.JsonPrimitive("""{"tag":"old"}"""))

        assertEquals("old", legacy.toModel().reverse!!["tag"]!!.jsonPrimitive.content)
    }

    @Test
    fun colonEmailIsRefusedOnlyWithAMasqueInbound() {
        val masque = InboundSlim(id = 1, protocol = "masque")
        val vless = InboundSlim(id = 2, protocol = "vless")
        val base = editorFor(listed, masque, vless).copy(email = "user:1")

        assertTrue(base.masqueEmailInvalid)
        assertFalse(base.canSave)
        assertFalse(base.copy(selectedInboundIds = setOf(2)).masqueEmailInvalid)
        assertFalse(base.copy(email = "user-1").masqueEmailInvalid)
    }
}
