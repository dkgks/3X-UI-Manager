package net.yukh.xui.shared.api

private val VERSION_PREFIX = Regex("""^v?(\d+)\.(\d+)(?:\.(\d+))?""")

/**
 * Whether a panel version string ("3.8.0", "v3.8.0", "3.8.0-beta1") is at least
 * [major].[minor].[patch]. An empty or unparsable version answers false, so a
 * feature that needs a newer panel stays hidden instead of failing against a
 * panel that cannot serve it. Mirrors the Android app's PanelVersion.kt.
 */
fun panelVersionAtLeast(version: String, major: Int, minor: Int, patch: Int = 0): Boolean {
    val g = VERSION_PREFIX.find(version.trim())?.groupValues ?: return false
    val actual = listOf(
        g[1].toIntOrNull() ?: return false,
        g[2].toIntOrNull() ?: return false,
        g[3].toIntOrNull() ?: 0,
    )
    val wanted = listOf(major, minor, patch)
    for (i in actual.indices) {
        if (actual[i] != wanted[i]) return actual[i] > wanted[i]
    }
    return true
}

/** Panel v3.8.0 features the app offers only when the panel reports that version. */
fun supportsPanel380(version: String): Boolean = panelVersionAtLeast(version, 3, 8, 0)
