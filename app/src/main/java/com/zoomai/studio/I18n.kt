package com.zoomai.studio

/** App language. FA is the default (v0.1 shipped in Persian); EN added in v0.2. */
enum class Lang { FA, EN }

/**
 * Tiny bilingual string helper. Every UI label calls [s] with its Persian and
 * English text, so the whole app switches language from one persisted setting.
 */
object I18n {
    var lang: Lang = Lang.FA
    fun s(fa: String, en: String): String = if (lang == Lang.FA) fa else en
    val isRtl: Boolean get() = lang == Lang.FA
}
