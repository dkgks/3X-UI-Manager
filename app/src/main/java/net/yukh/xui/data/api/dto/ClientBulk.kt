package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable

/** Body for POST /panel/api/clients/bulkEnable | bulkDisable. */
@Serializable
data class BulkEmailsRequest(val emails: List<String>)

/**
 * Body for POST /panel/api/clients/bulkAdjust. Shifts each client's expiry by
 * [addDays] (may be negative) and traffic limit by [addBytes] (may be negative);
 * [flow] sets the XTLS flow — "" leaves it untouched, "none" clears it, and the
 * vision values set it (only on flow-eligible inbounds).
 *
 * Panel v3.8.0 adds [limitHwid] — null leaves every device limit alone (the app's
 * Json drops nulls, so the key is not sent) — and [adTag]: "" leaves the MTProto
 * sponsor tag alone, "none" clears it, a 32-hex tag sets it (MTProto inbounds only).
 * Older panels silently ignore both keys, so the dialog offers them on 3.8.0+ only.
 */
@Serializable
data class BulkAdjustRequest(
    val emails: List<String>,
    val addDays: Int = 0,
    val addBytes: Long = 0,
    val flow: String = "",
    val limitHwid: Int? = null,
    val adTag: String = "",
)

/** Result of POST /panel/api/clients/bulkAdjust: how many clients changed, and why
 *  the rest were skipped (same {email, reason} entries as the bulk delete report). */
@Serializable
data class BulkAdjustResult(val adjusted: Int = 0, val skipped: List<BulkDeleteSkip> = emptyList())

/** The bulk-adjust MTProto ad tag as the panel accepts it: empty (no change),
 *  "none" (clear) or exactly 32 hex characters. */
fun isValidBulkAdTag(value: String): Boolean =
    value.isEmpty() || value == "none" || Regex("^[0-9a-fA-F]{32}$").matches(value)

/** Body for POST /panel/api/clients/bulkDel. */
@Serializable
data class BulkDelRequest(val emails: List<String>, val keepTraffic: Boolean = false)

/** Result of POST /panel/api/clients/bulkDel: how many went, and why the rest did not. */
@Serializable
data class BulkDeleteResult(val deleted: Int = 0, val skipped: List<BulkDeleteSkip> = emptyList())

@Serializable
data class BulkDeleteSkip(val email: String = "", val reason: String = "")

/** Body for POST /panel/api/clients/import — [data] is the stringified JSON array
 *  of {client, inboundIds} entries (the same shape GET /clients/export returns). */
@Serializable
data class ClientImportRequest(val data: String)

/** One entry of a client's IP log (POST /panel/api/clients/ips/:email).
 *  [node] is the node the IP connects through, or "" on the local panel. */
@Serializable
data class ClientIpInfo(val ip: String = "", val time: String = "", val node: String = "")
