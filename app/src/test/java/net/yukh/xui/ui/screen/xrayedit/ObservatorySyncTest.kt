package net.yukh.xui.ui.screen.xrayedit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import net.yukh.xui.data.json.child
import net.yukh.xui.data.json.putStrings
import net.yukh.xui.data.json.strings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** A leastLoad balancer without a burstObservatory stops Xray with "not all dependencies are resolved". */
class ObservatorySyncTest {

    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private fun template(balancers: String, extra: String = ""): JsonObject =
        obj("""{"outbounds":[],"routing":{"balancers":[$balancers]}$extra}""")

    private fun selectors(o: JsonObject?): List<String> =
        o?.strings("subjectSelector") ?: error("no observer")

    @Test
    fun `leastLoad gets a default burst observer that also watches leastPing selectors`() {
        val t = template(
            """{"tag":"ll","selector":["a","b"],"strategy":{"type":"leastLoad"}},""" +
                """{"tag":"lp","selector":["b","c"],"strategy":{"type":"leastPing"}}""",
            ""","observatory":{"probeURL":"x"}""",
        )
        val out = syncObservatories(t)
        assertNull(out["observatory"])
        val burst = out["burstObservatory"] as JsonObject
        assertEquals(listOf("a", "b", "c"), selectors(burst))
        assertEquals(DEFAULT_PING_CONFIG, burst["pingConfig"])
    }

    @Test
    fun `random or roundRobin needs a burst observer only with a fallbackTag`() {
        for (type in listOf("random", "roundRobin")) {
            val withFallback = template("""{"tag":"r","selector":["a"],"strategy":{"type":"$type"},"fallbackTag":"direct"}""")
            assertEquals(listOf("a"), selectors(syncObservatories(withFallback)["burstObservatory"] as JsonObject))
        }
        val noStrategy = template("""{"tag":"r","selector":["a"],"fallbackTag":"direct"}""")
        assertEquals(listOf("a"), selectors(syncObservatories(noStrategy)["burstObservatory"] as JsonObject))

        val plain = syncObservatories(template("""{"tag":"r","selector":["a"]}""", ""","burstObservatory":{}"""))
        assertFalse("burstObservatory" in plain)
        assertFalse("observatory" in plain)
    }

    @Test
    fun `leastPing alone gets a plain observer and drops the burst one`() {
        val t = template(
            """{"tag":"lp","selector":["a","","a"],"strategy":{"type":"leastPing"}},{"tag":"r","selector":["z"]}""",
            ""","burstObservatory":{"pingConfig":{}}""",
        )
        val out = syncObservatories(t)
        assertNull(out["burstObservatory"])
        assertEquals(DEFAULT_OBSERVATORY.putStrings("subjectSelector", listOf("a")), out["observatory"])
    }

    @Test
    fun `no observer-backed balancer removes both observers`() {
        val out = syncObservatories(obj("""{"routing":{},"observatory":{},"burstObservatory":{}}"""))
        assertEquals(obj("""{"routing":{}}"""), out)
    }

    @Test
    fun `existing observer settings survive while selectors are rewritten`() {
        val burst = """{"subjectSelector":["old"],"pingConfig":{"destination":"https://e/204","sampling":5},"extra":1}"""
        val out = syncObservatories(template("""{"tag":"ll","selector":["n"],"strategy":{"type":"leastLoad"}}""", ""","burstObservatory":$burst"""))
        assertEquals(obj("""{"subjectSelector":["n"],"pingConfig":{"destination":"https://e/204","sampling":5},"extra":1}"""), out["burstObservatory"])

        val obs = """{"subjectSelector":["old"],"probeURL":"https://e/204","enableConcurrency":false}"""
        val plain = syncObservatories(template("""{"tag":"lp","selector":["n"],"strategy":{"type":"leastPing"}}""", ""","observatory":$obs"""))
        assertEquals(obj("""{"subjectSelector":["n"],"probeURL":"https://e/204","enableConcurrency":false}"""), plain["observatory"])
    }

    @Test
    fun `patching fills defaults and touches only the active object`() {
        val t = obj("""{"burstObservatory":{"subjectSelector":["a"],"pingConfig":{"timeout":"9s"}}}""")
        val ping = t.patchPingConfig("sampling", JsonPrimitive(4)).child("burstObservatory").child("pingConfig")
        assertEquals(JsonPrimitive("9s"), ping["timeout"])
        assertEquals(JsonPrimitive(4), ping["sampling"])
        assertEquals(DEFAULT_PING_CONFIG["destination"], ping["destination"])
        assertNull(t.patchPingConfig("sampling", JsonPrimitive(4))["observatory"])

        val o = obj("""{"observatory":{"subjectSelector":["a"]}}""").patchObservatory("enableConcurrency", JsonPrimitive(false))
        assertEquals(obj("""{"subjectSelector":["a"],"probeURL":"https://www.google.com/generate_204","probeInterval":"1m","enableConcurrency":false}"""), o["observatory"])
    }
}
