package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable

/**
 * One device registered against a client's subscription (panel v3.7.0). The
 * panel counts these against the client's HWID limit; removing one just makes
 * that device register again on its next subscription fetch.
 */
@Serializable
data class ClientHwid(
    val id: Int = 0,
    val subId: String = "",
    val firstSeen: Long = 0,
    val lastSeen: Long = 0,
    val userAgent: String = "",
    val deviceOs: String = "",
    val osVersion: String = "",
    val deviceModel: String = "",
    // Panel v3.8.0: the first 12 characters of the stored HWID hash — enough to
    // tell two similar devices apart without exposing the hash itself.
    val fingerprint: String = "",
) {
    /** What to show as the device's name: model, else OS, else the user agent. */
    val label: String
        get() = listOf(deviceModel, deviceOs, osVersion)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { userAgent }
}
