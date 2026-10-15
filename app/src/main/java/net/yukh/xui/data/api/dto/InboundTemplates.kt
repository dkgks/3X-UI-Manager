package net.yukh.xui.data.api.dto

import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Minimal valid default JSON for a fresh inbound, per protocol. The user
 * tweaks these in the editor's JSON fields; the panel validates on save.
 * Kept deliberately small — clients are added separately, TLS/Reality is
 * configured by editing streamSettings.
 */
object InboundTemplates {

    // The panel's protocol validator knows "mixed" (SOCKS + HTTP), never "socks".
    val PROTOCOLS = listOf("vless", "vmess", "trojan", "shadowsocks", "mixed", "http")

    /** The protocols a new inbound can take on this panel: TUIC arrived in 3.8.0, MASQUE in 3.10.0. */
    fun protocols(panel380: Boolean, panel3100: Boolean = false): List<String> =
        PROTOCOLS + listOfNotNull("tuic".takeIf { panel380 }, "masque".takeIf { panel3100 })

    val TRAFFIC_RESET = listOf("never", "hourly", "daily", "weekly", "monthly")

    /** The panel's inbound method list: 2022-blake3-chacha20-poly1305 is refused on a multi-user inbound. */
    val SS_INBOUND_METHODS = listOf(
        "aes-256-gcm", "chacha20-poly1305", "chacha20-ietf-poly1305", "xchacha20-ietf-poly1305",
        "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm",
    )
    const val SS_DEFAULT_METHOD = "2022-blake3-aes-256-gcm"

    fun isSs2022(method: String): Boolean = method.startsWith("2022")

    /** A random server key for [method], as the panel makes it: base64 of 16 bytes for aes-128, else 32. */
    @OptIn(ExperimentalEncodingApi::class)
    fun shadowsocksKey(method: String): String {
        val bytes = ByteArray(if (method == "2022-blake3-aes-128-gcm") 16 else 32)
        SecureRandom().nextBytes(bytes)
        return Base64.encode(bytes)
    }

    const val MASQUE_DEFAULT_PATH = "/.well-known/masque/ip/*/*/"
    val MASQUE_DEFAULT_ADDRESS_POOL = listOf("10.14.0.1/24", "fd14::1/64")

    private fun randomLowerAndNum(len: Int): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        val rnd = SecureRandom()
        return buildString(len) { repeat(len) { append(alphabet[rnd.nextInt(alphabet.length)]) } }
    }

    fun settings(protocol: String): String = when (protocol) {
        "vless" -> """{
  "clients": [],
  "decryption": "none",
  "fallbacks": []
}"""
        "vmess" -> """{
  "clients": []
}"""
        "trojan" -> """{
  "clients": [],
  "fallbacks": []
}"""
        "shadowsocks" -> """{
  "method": "$SS_DEFAULT_METHOD",
  "password": "${shadowsocksKey(SS_DEFAULT_METHOD)}",
  "network": "tcp,udp",
  "clients": [],
  "ivCheck": false
}"""
        "mixed" -> """{
  "auth": "password",
  "accounts": [{"user": "${randomLowerAndNum(8)}", "pass": "${randomLowerAndNum(12)}"}],
  "udp": false,
  "ip": "127.0.0.1"
}"""
        "http" -> """{
  "accounts": []
}"""
        // Panel 3.8.0 serves TUIC from its own tuic-server process. These are the web
        // form's defaults; the certificate and key paths must be filled in before use.
        "tuic" -> """{
  "server": {
    "certificate": "",
    "private_key": "",
    "congestion_control": "bbr",
    "alpn": ["h3", "spdy/3.1"],
    "udp_relay_mode": "native",
    "zero_rtt_handshake": true,
    "log_level": "info",
    "max_idle_time": 15,
    "authentication_timeout": 3,
    "max_udp_relay_packet_size": 1500,
    "sni": ""
  },
  "clients": []
}"""
        "masque" -> """{
  "clients": [],
  "address": ["${MASQUE_DEFAULT_ADDRESS_POOL.joinToString("\", \"")}"]
}"""
        else -> "{}"
    }

    fun streamSettings(protocol: String): String = when (protocol) {
        "vless", "vmess", "trojan" -> """{
  "network": "tcp",
  "security": "none"
}"""
        // MASQUE is HTTP/3 over QUIC and TLS-only; the panel seeds Hysteria's QUIC TLS (ALPN h3).
        "masque" -> """{
  "network": "masque",
  "security": "tls",
  "masqueSettings": {"path": "$MASQUE_DEFAULT_PATH"},
  "tlsSettings": {
    "serverName": "",
    "minVersion": "1.2",
    "maxVersion": "1.3",
    "certificates": [
      {"certificateFile": "", "keyFile": "", "ocspStapling": 0, "oneTimeLoading": false, "usage": "encipherment", "buildChain": false}
    ],
    "alpn": ["h3"],
    "settings": {"fingerprint": ""}
  }
}"""
        else -> "{}"
    }

    fun sniffing(): String = """{
  "enabled": false,
  "destOverride": ["http", "tls", "quic"],
  "metadataOnly": false,
  "routeOnly": false
}"""
}

/**
 * Which inbound form sections apply to a protocol, after the panel's
 * protocol-capabilities.ts, so the editor cannot turn e.g. MASQUE into tcp.
 */
object InboundCapabilities {
    private val STREAM_PROTOCOLS = setOf("vmess", "vless", "trojan", "shadowsocks", "hysteria", "wireguard", "tunnel", "masque")
    private val FIXED_TRANSPORT = setOf("hysteria", "masque", "wireguard", "tunnel")
    private val TLS_PROTOCOLS = setOf("vmess", "vless", "trojan", "shadowsocks")
    private val TLS_NETWORKS = setOf("tcp", "ws", "http", "grpc", "httpupgrade", "xhttp")
    private val REALITY_PROTOCOLS = setOf("vless", "trojan")
    private val REALITY_NETWORKS = setOf("tcp", "http", "grpc", "xhttp")

    /** Xray streamSettings apply (TUN, mixed, http, TUIC, MTProto… have none). */
    fun hasStream(protocol: String): Boolean = protocol in STREAM_PROTOCOLS

    fun hasSniffing(protocol: String): Boolean = protocol !in setOf("mtproto", "amneziawg", "tuic")

    fun hasSelectableNetwork(protocol: String): Boolean = hasStream(protocol) && protocol !in FIXED_TRANSPORT

    /** Security choices for this protocol + network; empty = no security section at all. */
    fun securities(protocol: String, network: String): List<String> = when {
        protocol == "wireguard" || protocol == "tunnel" -> emptyList()
        protocol == "hysteria" || protocol == "masque" -> listOf("tls")
        // xray-core would read an XDRIVE listener's TLS as the storage API's client TLS.
        network == "xdrive" -> listOf("none")
        else -> listOfNotNull(
            "none",
            "tls".takeIf { protocol in TLS_PROTOCOLS && network in TLS_NETWORKS },
            "reality".takeIf { protocol in REALITY_PROTOCOLS && network in REALITY_NETWORKS },
        )
    }
}
