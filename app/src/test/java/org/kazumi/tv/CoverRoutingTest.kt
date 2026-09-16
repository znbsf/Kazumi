package org.kazumi.tv
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import org.kazumi.tv.data.CoverRouting
class CoverRoutingTest {
    @Test fun apiRedirectAndCdnUseMirrorWithoutLosingQuery() {
        for(raw in listOf("https://lain.bgm.tv/pic/crt/l/aa/123.jpg?r=7", "https://api.bgm.tv/v0/subjects/547888/image?type=large", "https://lain.bgm.tv/pic/cover/l/aa/bb/test.jpg")) {
            val url=raw.toHttpUrl(); val target=CoverRouting.target(url,true)
            assertEquals("wsrv.nl",target.host); assertEquals(raw,target.queryParameter("url"))
            assertEquals(url,CoverRouting.target(url,false))
        }
    }
    @Test fun neverRouteNonCoverOrUnrelatedHosts() {
        for(raw in listOf("https://api.bgm.tv/v0/me", "https://api.bgm.tv/v0/subjects/1", "https://example.com/image", "https://lain.bgm.tv/private")) {
            val url=raw.toHttpUrl(); assertEquals(url,CoverRouting.target(url,true))
        }
    }
}
