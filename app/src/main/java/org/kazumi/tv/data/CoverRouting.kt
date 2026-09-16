package org.kazumi.tv.data
import okhttp3.HttpUrl

object CoverRouting {
    fun target(url: HttpUrl, mirrored: Boolean): HttpUrl {
        val publicCover=(url.host=="lain.bgm.tv" && (url.encodedPath.startsWith("/pic/cover/") || url.encodedPath.startsWith("/pic/crt/"))) ||
            (url.host=="api.bgm.tv" && Regex("/v0/subjects/[0-9]+/image").matches(url.encodedPath))
        if(!mirrored || !publicCover)return url
        // API image URLs redirect to the CDN; route the initial public URL so that
        // application interceptors do not miss the redirect on the television.
        return HttpUrl.Builder().scheme("https").host("wsrv.nl")
            .addQueryParameter("url",url.toString()).addQueryParameter("w","600").build()
    }
}
