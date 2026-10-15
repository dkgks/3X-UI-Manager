package net.yukh.xui.ui.screen.xrayedit

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

internal fun serverToObject(el: JsonElement): JsonObject = when (el) {
    is JsonObject -> el
    is JsonPrimitive -> JsonObject(mapOf("address" to el))
    else -> JsonObject(emptyMap())
}

/** Collapse a server object back to a bare address string, as the panel's DnsServerModal
 *  does — but only when every other key is one we know and at its default. Anything else
 *  (`id` used by `dns.script`, `tag`, `clientIP`, keys added by newer panels) keeps the object. */
internal fun serverToElement(o: JsonObject): JsonElement {
    val address = o["address"] as? JsonPrimitive
    if (address == null || !address.isString) return o
    val plain = o.all { (key, value) -> key == "address" || isDefaultServerValue(key, value) }
    return if (plain) address else o
}

private fun isDefaultServerValue(key: String, v: JsonElement): Boolean {
    if (v is JsonNull) return true
    val p = v as? JsonPrimitive
    return when (key) {
        "port" -> p?.intOrNull == 53
        "domains", "expectIPs", "expectedIPs", "unexpectedIPs" -> v is JsonArray && v.isEmpty()
        "queryStrategy" -> p != null && p.isString && (p.content.isEmpty() || p.content == "UseIP")
        "skipFallback", "disableCache", "finalQuery", "serveStale" -> p?.booleanOrNull == false
        "serveExpiredTTL" -> p?.intOrNull == 0
        "timeoutMs" -> p?.intOrNull == 4000
        "id", "tag", "clientIP" -> p != null && p.isString && p.content.isEmpty()
        else -> false
    }
}

/** The panel writes `expectedIPs`, older configs `expectIPs` (Xray reads both): edit
 *  whichever the server already uses, new servers keep the app's `expectIPs`. */
internal fun JsonObject.expectedIpsKey(): String = if ("expectedIPs" in this) "expectedIPs" else "expectIPs"
