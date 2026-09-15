package net.yukh.xui.data.api.dto

/**
 * Minimal valid default JSON for a fresh inbound, per protocol. The user
 * tweaks these in the editor's JSON fields; the panel validates on save.
 * Kept deliberately small — clients are added separately, TLS/Reality is
 * configured by editing streamSettings.
 */
object InboundTemplates {

    val PROTOCOLS = listOf("vless", "vmess", "trojan", "shadowsocks", "socks", "http")

    /** The protocols a new inbound can take on this panel: TUIC arrived in panel 3.8.0. */
    fun protocols(panel380: Boolean): List<String> = if (panel380) PROTOCOLS + "tuic" else PROTOCOLS

    val TRAFFIC_RESET = listOf("never", "hourly", "daily", "weekly", "monthly")

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
  "method": "2022-blake3-aes-256-gcm",
  "password": "",
  "network": "tcp,udp",
  "clients": []
}"""
        "socks" -> """{
  "auth": "password",
  "accounts": [],
  "udp": true
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
        else -> "{}"
    }

    fun streamSettings(protocol: String): String = when (protocol) {
        "vless", "vmess", "trojan" -> """{
  "network": "tcp",
  "security": "none"
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
