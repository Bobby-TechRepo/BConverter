package io.bsoft.bconverter.domain.settings

/**
 * The languages the user can pick inside the app. [tag] is a BCP-47 language tag; SYSTEM (empty) follows the phone.
 * [nativeName] is shown in its own language so a person can always find theirs; SYSTEM has no name here (it is a string resource).
 */
enum class AppLanguage(val tag: String, val nativeName: String?) {
    SYSTEM("", null),
    ENGLISH("en", "English"),
    FRENCH("fr", "Français"),
    SPANISH("es", "Español"),
    CHINESE_SIMPLIFIED("zh-CN", "简体中文"),
    JAPANESE("ja", "日本語");

    companion object {
        /** Maps the app's current locale list (e.g. "fr-FR" or "zh-Hans-CN,en") to a choice; unknown or empty means SYSTEM. */
        fun fromTags(tags: String?): AppLanguage {
            val first = tags?.split(',')?.firstOrNull()?.trim().orEmpty()
            if (first.isEmpty()) return SYSTEM
            val language = first.split('-', '_').first().lowercase()
            return entries.firstOrNull { it != SYSTEM && it.tag.substringBefore('-') == language } ?: SYSTEM
        }
    }
}

/** Reads and changes the app's language. Changing it recreates the visible screen in the new language. */
interface LanguageController {
    fun current(): AppLanguage
    fun set(language: AppLanguage)
}
