package net.yukh.xui.data.api.dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class InboundTemplatesTest {

    private fun keyBytes(key: String) = Base64.decode(key).size

    @Test
    fun `shadowsocks 2022 keys have the length xray-core's ParseKey wants`() {
        assertEquals(16, keyBytes(InboundTemplates.shadowsocksKey("2022-blake3-aes-128-gcm")))
        assertEquals(32, keyBytes(InboundTemplates.shadowsocksKey("2022-blake3-aes-256-gcm")))
        assertNotEquals(
            InboundTemplates.shadowsocksKey("2022-blake3-aes-256-gcm"),
            InboundTemplates.shadowsocksKey("2022-blake3-aes-256-gcm"),
        )
    }

    @Test
    fun `a new shadowsocks inbound carries a usable server key, never an empty one`() {
        val s = Json.parseToJsonElement(InboundTemplates.settings("shadowsocks")).jsonObject
        assertEquals("2022-blake3-aes-256-gcm", s["method"]!!.jsonPrimitive.content)
        assertEquals(32, keyBytes(s["password"]!!.jsonPrimitive.content))
    }

    @Test
    fun `inbound method list leaves out the chacha20 2022 method the panel refuses`() {
        assertFalse("2022-blake3-chacha20-poly1305" in InboundTemplates.SS_INBOUND_METHODS)
        assertTrue(InboundTemplates.SS_DEFAULT_METHOD in InboundTemplates.SS_INBOUND_METHODS)
    }

    @Test
    fun `protocol list offers mixed instead of socks and masque only on 3_10`() {
        val old = InboundTemplates.protocols(panel380 = true, panel3100 = false)
        val new = InboundTemplates.protocols(panel380 = true, panel3100 = true)
        assertTrue("mixed" in old)
        assertFalse("socks" in old)
        assertFalse("masque" in old)
        assertTrue("masque" in new)
    }

    @Test
    fun `masque template is the panel's TLS-only masque stream with the default pool`() {
        val stream = Json.parseToJsonElement(InboundTemplates.streamSettings("masque")).jsonObject
        assertEquals("masque", stream["network"]!!.jsonPrimitive.content)
        assertEquals("tls", stream["security"]!!.jsonPrimitive.content)
        val settings = Json.parseToJsonElement(InboundTemplates.settings("masque")).jsonObject
        assertEquals("[\"10.14.0.1/24\",\"fd14::1/64\"]", settings["address"].toString())
    }

    @Test
    fun `masque, xdrive and tun only get the sections that keep them working`() {
        assertEquals(listOf("tls"), InboundCapabilities.securities("masque", "masque"))
        assertFalse(InboundCapabilities.hasSelectableNetwork("masque"))
        assertEquals(listOf("none"), InboundCapabilities.securities("vless", "xdrive"))
        assertFalse(InboundCapabilities.hasStream("tun"))
        assertTrue(InboundCapabilities.hasSniffing("tun"))
        assertEquals(listOf("none", "tls", "reality"), InboundCapabilities.securities("vless", "tcp"))
    }
}
