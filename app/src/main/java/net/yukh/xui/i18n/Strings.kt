package net.yukh.xui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

/**
 * Supported UI languages.
 */
const val LANG_EN = "en"
const val LANG_RU = "ru"
const val LANG_ZH = "zh"

/** The stored preference meaning "follow the device language" — the default. */
const val LANG_SYSTEM = ""

/**
 * Current UI language for the composition. Provided at the app root from
 * [LanguageState]. Defaults to English.
 */
val LocalAppLanguage = compositionLocalOf { LANG_EN }

/**
 * The translation table for a language, or null when the language has no
 * table — English is the source language, so every missing key falls back
 * to English.
 */
private fun tableFor(lang: String): Map<String, String>? = when (lang) {
    LANG_RU -> ruStrings
    LANG_ZH -> zhStrings
    else -> null
}

/**
 * Translate an English source string to the current UI language. The English
 * text IS the key — anything missing from the active table falls back to
 * English, so partial coverage degrades gracefully rather than breaking.
 */
@Composable
@ReadOnlyComposable
fun tr(en: String): String = tableFor(LocalAppLanguage.current)?.get(en) ?: en

/** Non-composable variant for the rare call site outside composition. */
fun tr(lang: String, en: String): String = tableFor(lang)?.get(en) ?: en
