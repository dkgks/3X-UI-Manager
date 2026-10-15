package net.yukh.xui.data.api.dto

import kotlinx.serialization.Serializable
import java.net.URI

/**
 * Subset of POST /panel/setting/all needed to build a client's subscription
 * URL. Only the subscription fields are declared; the endpoint returns many
 * more (ignoreUnknownKeys drops them).
 *
 * NOTE: on panel v3.3.0 the settings call moved to /panel/api/setting/all,
 * which an API token can reach — so this is populated with either a token or a
 * login session. Only panels older than v3.3.0 (session-only /panel/setting/all)
 * leave it empty under token auth.
 */
@Serializable
data class PanelSettings(
    val subEnable: Boolean = false,
    val subURI: String = "",
    val subPath: String = "/sub/",
    val subPort: Int = 0,
    val subDomain: String = "",
    val subCertFile: String = "",
    val subKeyFile: String = "",
    // Panel v3.8.0: whether the operator allowed encrypted Happ links in the
    // client QR window (Settings → Subscription → Happ → Subscription links).
    val happLinkEnable: Boolean = false,
    // The JSON subscription, the only one that carries MASQUE (panel 3.10.0).
    val subJsonEnable: Boolean = false,
    val subJsonPath: String = "/json/",
    val subJsonURI: String = "",
) {
    /**
     * Build the subscription URL for a client, replicating the panel's
     * BuildSubURIBase + GetDefaultSettings logic (web/service/setting.go).
     * Returns null when subscriptions are disabled or there's no subId.
     *
     * @param panelHost hostname taken from the connection profile's base URL,
     *   used only when subDomain is unset.
     */
    fun subscriptionUrl(panelHost: String, subId: String): String? {
        if (!subEnable || subId.isBlank()) return null
        return withSlash(subURI.ifBlank { subServerBase(panelHost) + normalizePath(subPath, "/sub/") }) + subId
    }

    /** The client's JSON subscription URL, like the panel's client QR window: only
     *  while both the subscription server and its JSON endpoint are switched on. */
    fun jsonSubscriptionUrl(panelHost: String, subId: String): String? {
        if (!subEnable || !subJsonEnable || subId.isBlank()) return null
        return withSlash(subJsonURI.ifBlank { subServerBase(panelHost) + normalizePath(subJsonPath, "/json/") }) + subId
    }

    /** scheme://authority of the subscription server (the panel's BuildSubURIBase). */
    private fun subServerBase(panelHost: String): String {
        val tls = subCertFile.isNotBlank() && subKeyFile.isNotBlank()
        val scheme = if (tls) "https" else "http"
        val domain = subDomain.ifBlank { panelHost }
        val authority = when {
            subPort == 443 && tls -> domain
            subPort == 80 && !tls -> domain
            subPort > 0 -> "$domain:$subPort"
            else -> domain
        }
        return "$scheme://$authority"
    }

    private fun normalizePath(path: String, fallback: String): String =
        withSlash(path.ifBlank { fallback }.let { if (it.startsWith("/")) it else "/$it" })

    private fun withSlash(s: String): String = if (s.endsWith("/")) s else "$s/"

    companion object {
        /** Extract a bare hostname from a base URL like https://host:2053/x-ui/. */
        fun hostOf(baseUrl: String): String = try {
            URI(baseUrl).host ?: baseUrl
        } catch (_: Exception) {
            baseUrl
        }
    }
}
