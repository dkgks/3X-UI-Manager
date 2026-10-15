package net.yukh.xui.i18n

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.yukh.xui.data.prefs.AppSettingsStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolve a stored preference to the language the UI actually renders in.
 * An explicit choice wins; otherwise the device language decides (mapping it
 * to a language we translate), and anything untranslated falls back to English.
 */
fun resolveLanguage(preference: String, systemLanguage: String): String = when (preference) {
    LANG_RU, LANG_EN, LANG_ZH -> preference
    else -> when {
        systemLanguage == LANG_RU -> LANG_RU
        systemLanguage.startsWith("zh") -> LANG_ZH
        else -> LANG_EN
    }
}

/**
 * App-wide UI language preference, backed by [AppSettingsStore].
 *
 * [preference] is what the user picked — including [LANG_SYSTEM]. The root
 * composable resolves it against the *current* configuration and republishes
 * the result through LocalAppLanguage. Callers outside composition use
 * [effective].
 */
@Singleton
class LanguageState @Inject constructor(
    private val store: AppSettingsStore,
) {
    private val _preference = MutableStateFlow(store.getLanguage())
    val preference: StateFlow<String> = _preference.asStateFlow()

    /** The language to render in right now, for non-composable call sites
     *  (notifications, the update checker). */
    fun effective(): String =
        resolveLanguage(_preference.value, java.util.Locale.getDefault().language)

    fun set(lang: String) {
        store.setLanguage(lang)
        _preference.value = lang
    }
}
