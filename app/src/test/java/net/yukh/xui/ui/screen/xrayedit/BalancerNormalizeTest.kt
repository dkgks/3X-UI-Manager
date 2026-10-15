package net.yukh.xui.ui.screen.xrayedit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Switching a balancer's strategy used to drop leastLoad's costs/baselines on the spot. */
class BalancerNormalizeTest {

    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private val settings = """{"expected":2,"baselines":["1s"],"costs":[{"match":"a","value":1}]}"""

    @Test
    fun `leastLoad keeps its settings`() {
        val b = obj("""{"tag":"b","strategy":{"type":"leastLoad","settings":$settings}}""")
        assertEquals(b, normalizeBalancer(b))
    }

    @Test
    fun `other strategies drop settings and random drops the strategy`() {
        val ping = obj("""{"tag":"b","strategy":{"type":"leastPing","settings":$settings}}""")
        assertEquals(obj("""{"tag":"b","strategy":{"type":"leastPing"}}"""), normalizeBalancer(ping))
        val random = obj("""{"tag":"b","strategy":{"type":"random","settings":$settings}}""")
        assertEquals(obj("""{"tag":"b"}"""), normalizeBalancer(random))
    }
}
