package org.kazumi.tv.playback

/** UI and resolver share this contract, independent of the chosen player backend. */
data class PlaybackRequest(val url: String, val headers: Map<String, String>, val title: String, val resumeKey: String = "", val mimeType: String? = null, val offlineId:String?=null)

object MediaAddress {
    fun isMedia(url: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        uri.scheme in listOf("https", "http") && uri.path.orEmpty().lowercase().let {
            it.endsWith(".m3u8") || it.endsWith(".mp4") || it.endsWith(".mkv") || it.endsWith(".mpd")
        }
    }.getOrDefault(false)
}
