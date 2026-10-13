package net.yukh.xui.ui.screen.xrayedit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Saving a DNS server must never shrink it to a bare address while it still carries
 *  data — panel 3.10's `id` (picked by `dns.script`) was lost that way. */
class DnsServerJsonTest {

    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    @Test
    fun `a server at panel defaults collapses to its address`() {
        val o = obj(
            """{"address":"8.8.8.8","port":53,"domains":[],"expectedIPs":[],"expectIPs":[],"unexpectedIPs":[],
               "queryStrategy":"UseIP","skipFallback":false,"disableCache":false,"finalQuery":false,
               "serveStale":false,"serveExpiredTTL":0,"timeoutMs":4000,"id":"","tag":"","clientIP":""}""",
        )
        assertEquals(JsonPrimitive("8.8.8.8"), serverToElement(o))
        assertEquals(JsonPrimitive("1.1.1.1"), serverToElement(obj("""{"address":"1.1.1.1"}""")))
    }

    @Test
    fun `a non-default or unknown key keeps the object`() {
        val cases = listOf(
            """{"address":"8.8.8.8","id":"google"}""",
            """{"address":"8.8.8.8","tag":"dns-out"}""",
            """{"address":"8.8.8.8","clientIP":"1.2.3.4"}""",
            """{"address":"8.8.8.8","skipFallback":true}""",
            """{"address":"8.8.8.8","expectedIPs":["geoip:ru"]}""",
            """{"address":"8.8.8.8","timeoutMs":2000}""",
            """{"address":"8.8.8.8","serveExpiredTTL":60}""",
            """{"address":"8.8.8.8","port":5353}""",
            """{"address":"8.8.8.8","someFutureKey":1}""",
        )
        for (json in cases) {
            val o = obj(json)
            assertEquals(json, o, serverToElement(o))
        }
    }
}
