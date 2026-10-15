package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Body shape for inbound create (POST /inbounds/add) and update
 * (POST /inbounds/update/:id), and also the parse target for
 * GET /inbounds/get/:id.
 *
 * settings/streamSettings/sniffing are kept as raw [JsonElement]: the panel's
 * Inbound.UnmarshalJSON accepts them as either a JSON object or a
 * JSON-encoded string, and MarshalJSON emits them as objects — so round-trip
 * as objects and avoid string-escaping entirely. Unknown response-only fields
 * (up/down/clientStats/…) are dropped by ignoreUnknownKeys.
 *
 * tag/subSortIndex/excludeFromSub/trafficResetDay have no editor field but the
 * panel's update overwrites them from the body, so an edit must send the loaded
 * values back (an empty tag makes the panel regenerate a custom one).
 */
@Serializable
data class InboundModel(
    val id: Int = 0,
    val remark: String = "",
    val enable: Boolean = true,
    val listen: String = "",
    val port: Int = 0,
    val protocol: String = "vless",
    val expiryTime: Long = 0,
    val total: Long = 0,
    val trafficReset: String = "never",
    val trafficResetDay: Int = 1,
    val tag: String = "",
    val subSortIndex: Int = 1,
    val excludeFromSub: Boolean = false,
    // Panel v3.7.0 (VLESS): opt this inbound out of automatic xtls-rprx-vision.
    val disableFlow: Boolean = false,
    val settings: JsonElement = JsonObject(emptyMap()),
    val streamSettings: JsonElement = JsonObject(emptyMap()),
    val sniffing: JsonElement = JsonObject(emptyMap()),
    val nodeId: Int? = null,
)
