package org.kazumi.tv

import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import org.kazumi.tv.playback.*
import org.kazumi.tv.rules.*

class MediaDiscoveryTest {
    private fun rule(config: JSONObject? = null) = SourceRule(JSONObject().put("name","fixture").put("baseURL","https://example.org").apply { config?.let { put("antiCrawlerConfig",it) } })
    @Test fun htmlPretendingToBeVideoIsRejected() {
        val bytes="<!doctype html><html>blocked</html>".toByteArray()
        assertNull(MediaProbe.mime(bytes,bytes.size,"video/mp4"))
    }
    @Test fun detectsExtensionlessManifestWithBom() {
        val bytes="\uFEFF#EXTM3U\n#EXTINF:3,\nsegment.ts".toByteArray()
        assertEquals("application/x-mpegURL",MediaProbe.mime(bytes,bytes.size,"text/plain"))
    }
    @Test fun titleChallengeDiffersFromOrdinarySearchText() {
        assertThrows(SourceVerificationRequired::class.java) { SourcePageChecks.check(rule(),"<html><title>系统安全验证</title></html>","https://example.org/search") }
        val ordinary="<html><title>搜索结果</title><p>剧情中出现人机验证</p></html>"
        assertEquals(ordinary,SourcePageChecks.check(rule(),ordinary,"https://example.org/search"))
    }
    @Test fun configuredXpathAndTextRetainFailurePage() {
        for (type in listOf(1,2)) {
            val config=JSONObject().put("enabled",true).put("captchaDetectType",type).put("captchaDetectValue",if(type==1) "//*[@id='verify']" else "verification_required")
            val error=assertThrows(SourceVerificationRequired::class.java) { SourcePageChecks.check(rule(config),"<html><div id='verify'>verification_required</div></html>","https://example.org/episode/10") }
            assertEquals("https://example.org/episode/10",error.pageUrl)
        }
    }
}
