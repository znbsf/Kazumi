package org.kazumi.tv.domain

/** Only unambiguous normal episode labels; specials and decimals require manual confirmation. */
object EpisodeNumber {
    fun parse(label: String): Int? {
        val text = label.substringAfterLast(" · ").trim()
        val match = Regex("^(?:第\\s*|EP\\s*|Episode\\s*)?(\\d{1,4})(?:\\s*[集话話期])?$", RegexOption.IGNORE_CASE).matchEntire(text) ?: return null
        return match.groupValues[1].toIntOrNull()?.takeIf { it > 0 }
    }
}
