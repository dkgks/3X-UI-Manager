package net.yukh.xui.ui.screen.xrayedit

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.yukh.xui.data.json.array
import net.yukh.xui.data.json.child
import net.yukh.xui.data.json.put
import net.yukh.xui.data.json.putStrings
import net.yukh.xui.data.json.string
import net.yukh.xui.data.json.strings

internal const val OBSERVATORY = "observatory"
internal const val BURST_OBSERVATORY = "burstObservatory"
private const val PROBE_URL = "https://www.google.com/generate_204"

internal val DEFAULT_OBSERVATORY = JsonObject(
    linkedMapOf(
        "subjectSelector" to JsonArray(emptyList()),
        "probeURL" to JsonPrimitive(PROBE_URL),
        "probeInterval" to JsonPrimitive("1m"),
        "enableConcurrency" to JsonPrimitive(true),
    ),
)

internal val DEFAULT_PING_CONFIG = JsonObject(
    linkedMapOf(
        "destination" to JsonPrimitive(PROBE_URL),
        "interval" to JsonPrimitive("1m"),
        "connectivity" to JsonPrimitive(""),
        "timeout" to JsonPrimitive("5s"),
        "sampling" to JsonPrimitive(2),
        "httpMethod" to JsonPrimitive("HEAD"),
    ),
)

internal val DEFAULT_BURST_OBSERVATORY = JsonObject(
    linkedMapOf("subjectSelector" to JsonArray(emptyList()), "pingConfig" to DEFAULT_PING_CONFIG),
)

private fun JsonObject.balancerList(): List<JsonObject> =
    child("routing").array("balancers").mapNotNull { it as? JsonObject }

private fun JsonObject.strategyType(): String = child("strategy").string("type").ifBlank { "random" }

internal fun collectSelectors(list: List<JsonObject>): List<String> =
    list.flatMap { it.strings("selector") }.filter { it.isNotEmpty() }.distinct()

/** leastLoad, and random/roundRobin with a fallbackTag, make xray-core require an observer (#5605). */
internal fun balancerRequiresBurstObservatory(b: JsonObject): Boolean {
    val type = b.strategyType()
    return type == "leastLoad" || ((type == "random" || type == "roundRobin") && b.string("fallbackTag").isNotEmpty())
}

internal fun JsonObject.requiresBurstObservatory(): Boolean = balancerList().any(::balancerRequiresBurstObservatory)

/** The panel's syncObservatories: each observer exists only while a balancer needs it, and
 *  its subjectSelector follows the balancer selectors; probe settings are kept. */
internal fun syncObservatories(template: JsonObject): JsonObject {
    val balancers = template.balancerList()
    val leastPings = balancers.filter { it.strategyType() == "leastPing" }
    val required = balancers.filter(::balancerRequiresBurstObservatory)
    return when {
        required.isNotEmpty() -> {
            val burst = template[BURST_OBSERVATORY] as? JsonObject ?: DEFAULT_BURST_OBSERVATORY
            template.put(OBSERVATORY, null)
                .put(BURST_OBSERVATORY, burst.putStrings("subjectSelector", collectSelectors(required + leastPings)))
        }
        leastPings.isNotEmpty() -> {
            val obs = template[OBSERVATORY] as? JsonObject ?: DEFAULT_OBSERVATORY
            template.put(BURST_OBSERVATORY, null)
                .put(OBSERVATORY, obs.putStrings("subjectSelector", collectSelectors(leastPings)))
        }
        else -> template.put(BURST_OBSERVATORY, null).put(OBSERVATORY, null)
    }
}

private fun merged(vararg layers: JsonObject): JsonObject =
    JsonObject(LinkedHashMap<String, JsonElement>().apply { layers.forEach { putAll(it) } })

/** What the settings view shows: the stored observer over its defaults, or null when absent. */
internal fun JsonObject.observatoryView(): JsonObject? =
    (this[OBSERVATORY] as? JsonObject)?.let { merged(DEFAULT_OBSERVATORY, it) }

internal fun JsonObject.burstObservatoryView(): JsonObject? =
    (this[BURST_OBSERVATORY] as? JsonObject)?.let { merged(DEFAULT_BURST_OBSERVATORY, it).put("pingConfig", merged(DEFAULT_PING_CONFIG, it.child("pingConfig"))) }

internal fun JsonObject.patchObservatory(key: String, value: JsonElement): JsonObject =
    put(OBSERVATORY, merged(DEFAULT_OBSERVATORY, child(OBSERVATORY), JsonObject(mapOf(key to value))))

internal fun JsonObject.patchPingConfig(key: String, value: JsonElement): JsonObject {
    val current = child(BURST_OBSERVATORY)
    val ping = merged(DEFAULT_PING_CONFIG, current.child("pingConfig"), JsonObject(mapOf(key to value)))
    return put(BURST_OBSERVATORY, merged(DEFAULT_BURST_OBSERVATORY, current).put("pingConfig", ping))
}
