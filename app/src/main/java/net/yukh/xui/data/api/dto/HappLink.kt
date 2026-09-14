package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable

/**
 * Result of POST /panel/api/clients/happLink/:id (panel v3.8.0): the client's
 * subscription URL encrypted into a `happ://crypt5/…` link that only the Happ app
 * opens. The panel builds it on demand and stores nothing, so every call can
 * return a different link for the same subscription.
 */
@Serializable
data class HappLinkResult(val encryptedLink: String = "")

/** The panel's exact message when the subscription URL is over its 8192-byte limit. */
const val HAPP_SOURCE_TOO_LONG = "happ_source_too_long"

/** QR version 40 at error-correction level M holds at most this many bytes. */
const val QR_MAX_BYTES = 2331
