package net.yukh.xui.ui.screen.clients

import java.net.URLDecoder

/** An ML-KEM-768 key is far longer than an X25519 one (the panel's MLKEM768_MIN_KEY_LENGTH). */
private const val MLKEM768_MIN_KEY_LENGTH = 300

/**
 * A share link carrying a post-quantum key too big to scan as a QR: a non-empty ML-DSA-65
 * `pqv`, or a VLESS `encryption` whose auth key is ML-KEM-768. Mirrors the panel's
 * isPostQuantumLink (3.10.0); the REALITY ML-KEM hint and the X25519 prefix don't count.
 */
fun isPostQuantumLink(link: String): Boolean {
    val withoutRemark = link.substringBefore('#')
    val queryStart = withoutRemark.indexOf('?')
    if (queryStart < 0) return false
    val params = withoutRemark.substring(queryStart + 1).split('&').mapNotNull { pair ->
        if (pair.isEmpty()) return@mapNotNull null
        val key = decode(pair.substringBefore('='))
        val value = if ('=' in pair) decode(pair.substringAfter('=')) else ""
        key to value
    }
    fun first(name: String): String = params.firstOrNull { it.first == name }?.second.orEmpty()
    if (first("pqv").isNotEmpty()) return true
    val encryption = first("encryption")
    if (encryption.isEmpty() || encryption == "none") return false
    val authKey = encryption.split('.').lastOrNull { it.isNotEmpty() }.orEmpty()
    return authKey.length > MLKEM768_MIN_KEY_LENGTH
}

private fun decode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
