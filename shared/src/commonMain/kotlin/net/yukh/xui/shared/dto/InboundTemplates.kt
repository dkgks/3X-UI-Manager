package net.yukh.xui.shared.dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Minimal valid default JSON for a fresh inbound, per protocol (ported from the
 * Android app). The user tweaks settings/streamSettings/sniffing as JSON in the
 * editor; the panel validates on save. Clients are managed separately.
 */
object InboundTemplates {
    val PROTOCOLS = listOf("vless", "vmess", "trojan", "shadowsocks", "socks", "http")

    /** The protocols a new inbound can take on this panel: TUIC arrived in panel 3.8.0. */
    fun protocols(panel380: Boolean): List<String> = if (panel380) PROTOCOLS + "tuic" else PROTOCOLS
    val TRAFFIC_RESET = listOf("never", "hourly", "daily", "weekly", "monthly")

    fun settings(protocol: String): String = when (protocol) {
        "vless" -> "{\n  \"clients\": [],\n  \"decryption\": \"none\",\n  \"fallbacks\": []\n}"
        "vmess" -> "{\n  \"clients\": []\n}"
        "trojan" -> "{\n  \"clients\": [],\n  \"fallbacks\": []\n}"
        "shadowsocks" -> "{\n  \"method\": \"2022-blake3-aes-256-gcm\",\n  \"password\": \"\",\n  \"network\": \"tcp,udp\",\n  \"clients\": []\n}"
        "socks" -> "{\n  \"auth\": \"password\",\n  \"accounts\": [],\n  \"udp\": true\n}"
        "http" -> "{\n  \"accounts\": []\n}"
        // Panel 3.8.0 serves TUIC from its own tuic-server process. These are the web
        // form's defaults; the certificate and key paths must be filled in before use.
        "tuic" -> "{\n  \"server\": {\n    \"certificate\": \"\",\n    \"private_key\": \"\",\n    \"congestion_control\": \"bbr\",\n    \"alpn\": [\"h3\", \"spdy/3.1\"],\n    \"udp_relay_mode\": \"native\",\n    \"zero_rtt_handshake\": true,\n    \"log_level\": \"info\",\n    \"max_idle_time\": 15,\n    \"authentication_timeout\": 3,\n    \"max_udp_relay_packet_size\": 1500,\n    \"sni\": \"\"\n  },\n  \"clients\": []\n}"
        else -> "{}"
    }

    fun streamSettings(protocol: String): String = when (protocol) {
        "vless", "vmess", "trojan" -> "{\n  \"network\": \"tcp\",\n  \"security\": \"none\"\n}"
        else -> "{}"
    }

    fun sniffing(): String =
        "{\n  \"enabled\": false,\n  \"destOverride\": [\"http\", \"tls\", \"quic\"],\n  \"metadataOnly\": false,\n  \"routeOnly\": false\n}"
}

private val prettyJson = Json { prettyPrint = true; prettyPrintIndent = "  "; isLenient = true; ignoreUnknownKeys = true }
private val laxJson = Json { isLenient = true; ignoreUnknownKeys = true }

// Pretty-printed JSON of an inbound's sub-objects, for the editor's text fields.
fun InboundModel.settingsText(): String = prettyJson.encodeToString(JsonElement.serializer(), settings)
fun InboundModel.streamSettingsText(): String = prettyJson.encodeToString(JsonElement.serializer(), streamSettings)
fun InboundModel.sniffingText(): String = prettyJson.encodeToString(JsonElement.serializer(), sniffing)

/**
 * Build an inbound from the editor's string fields; returns null if any of the
 * three JSON blocks is invalid (the editor then blocks Save). Keeps all
 * kotlinx-serialization use inside :shared so composeApp stays JSON-free.
 */
fun buildInbound(
    base: InboundModel,
    protocol: String,
    remark: String,
    listen: String,
    port: Int,
    totalBytes: Long,
    trafficReset: String,
    disableFlow: Boolean,
    enable: Boolean,
    settingsText: String,
    streamSettingsText: String,
    sniffingText: String,
): InboundModel? {
    val s = parseJsonOrNull(settingsText) ?: return null
    val st = parseJsonOrNull(streamSettingsText) ?: return null
    val sn = parseJsonOrNull(sniffingText) ?: return null
    return base.copy(
        protocol = protocol, remark = remark, listen = listen, port = port,
        total = totalBytes, trafficReset = trafficReset, disableFlow = disableFlow, enable = enable,
        settings = s, streamSettings = st, sniffing = sn,
    )
}

private fun parseJsonOrNull(text: String): JsonElement? =
    try { laxJson.parseToJsonElement(text) } catch (e: Throwable) { null }
