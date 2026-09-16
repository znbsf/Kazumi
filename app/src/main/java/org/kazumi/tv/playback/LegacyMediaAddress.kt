package org.kazumi.tv.playback

import java.net.URI
import java.net.URLDecoder
import org.kazumi.tv.rules.SourceRule

/** Port of upstream decodeVideoSource: query values containing media URLs, not arbitrary script execution. */
object LegacyMediaAddress {
    fun extract(frameUrl: String): List<String> = runCatching {
        if (frameUrl.length > 16384) return emptyList()
        SourceRule.httpUrl(frameUrl)
        URI(frameUrl).rawQuery.orEmpty().split('&').take(32).mapNotNull { parameter ->
            val raw = parameter.substringAfter('=', "")
            val value = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull() ?: return@mapNotNull null
            value.takeIf { MediaAddress.isMedia(it) && runCatching { SourceRule.httpUrl(it) }.isSuccess }
        }.distinct().take(8)
    }.getOrDefault(emptyList())
}
