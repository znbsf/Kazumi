package org.kazumi.tv

import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import org.kazumi.tv.playback.PageMediaMetadata
import org.kazumi.tv.playback.SoraniPlayback
import org.kazumi.tv.playback.MediaResolutionFailure
import org.kazumi.tv.rules.*

class VerificationAndMetadataTest {
    @Test fun soraniRuleApiRouteDoesNotGuessOnUnrecognizedRulesOrLineParameters() {
        val rule=SourceRule(JSONObject("""{"name":"sorani","baseURL":"https://www.sorani.net","chapterApiConfig":{"request":{"url":"https://api.sorani.cc/sorani-cms/api/video/@source"}}}"""))
        assertEquals("4634" to "12.0",SoraniPlayback.ruleRoute("https://www.sorani.net/anime/mal/4634/episode/12.0",rule))
        assertNull(SoraniPlayback.ruleRoute("https://www.sorani.net/anime/mal/4634/episode/12.0?line=other",rule))
        assertNull(SoraniPlayback.ruleRoute("https://unrelated.example/anime/mal/4634/episode/12.0",rule))
        assertNull(SoraniPlayback.ruleRoute("https://www.sorani.net/anime/other/4634/episode/12.0",rule))
    }
    @Test fun soraniSelectsCurrentEpisodeRatherThanEarlierEpisodeList() {
        val html="""<script>env:{"PUBLIC_API_BASE":"https://api.sorani.cc/sorani-cms"};episodes:[{episodeId:1,videoId:4634}],selectedLineCode:"anime_jp_m3u8",episodeId:66037,episodeName:"第12集"</script>"""
        assertEquals("https://api.sorani.cc/sorani-cms/api/video/episode/66037/play?lineCode=anime_jp_m3u8",SoraniPlayback.apiUrl("https://www.sorani.net/anime/mal/4634/episode/12.0",html))
        assertNull(SoraniPlayback.apiUrl("https://unrelated.example/anime/mal/4634/episode/12.0",html))
        assertNull(SoraniPlayback.apiUrl("https://www.sorani.net/anime/mal/4634/episode/12.0",html.replace("api.sorani.cc","other.example")))
    }
    @Test fun soraniServerDeniedOrPreviewResponseCannotBecomePlayback() {
        for(extra in listOf("\"canPlay\":false","\"canPlay\":true,\"hasPreview\":true","\"canPlay\":true,\"advertisingRequired\":true")) {
            assertThrows(MediaResolutionFailure::class.java) { SoraniPlayback.permittedUrl(JSONObject("""{"code":200,"data":{$extra,"playUrl":"https://media.example/a.m3u8"}}""")) }
        }
        assertEquals("https://media.example/a.m3u8",SoraniPlayback.permittedUrl(JSONObject("""{"code":200,"data":{"canPlay":true,"playUrl":"https://media.example/a.m3u8"}}""")))
    }
    private fun snapshot(challenge:Boolean=false,ready:Boolean=true,acted:Boolean=false,failed:Boolean=false)=JSONObject()
        .put("challenge",challenge).put("ready",ready).put("acted",acted).put("failed",failed)
    @Test fun blankNavigationAndInitialNormalPageAreNotVerification() {
        val state=VerificationProgress()
        repeat(5) { assertFalse(state.observe(snapshot())) }
        assertFalse(state.observe(snapshot(challenge=true)))
        repeat(5) { assertFalse(state.observe(snapshot(ready=false))) }
        repeat(2) { assertFalse(state.observe(snapshot())) }
        assertTrue(state.observe(snapshot()))
    }
    @Test fun failedOrRemainingChallengeNeverPassesAndActionSurvivesNavigation() {
        val state=VerificationProgress();state.markAction()
        repeat(5) { assertFalse(state.observe(snapshot(challenge=true,acted=true))) }
        repeat(5) { assertFalse(state.observe(snapshot(failed=true))) }
        repeat(2) { assertFalse(state.observe(snapshot())) }
        assertTrue(state.observe(snapshot()))
    }
    @Test fun nestedMetadataPreservesPlusAndDecodesOnlyOneLayer() {
        val html="""<script>var player_aaaa={"vod_data":{"title":"test"},"encrypt":1,"url":"https%3A%2F%2Fmedia.example%2F%25E6%2597%25A0+12.mp4"};</script>"""
        assertEquals(listOf("https://media.example/%E6%97%A0+12.mp4"),PageMediaMetadata.extract(html))
    }
    @Test fun base64MetadataAndInvalidMediaAreHandledWithoutScriptEvaluation() {
        val value=java.util.Base64.getEncoder().encodeToString("https%3A%2F%2Fmedia.example%2Fa.m3u8".toByteArray())
        assertEquals(listOf("https://media.example/a.m3u8"),PageMediaMetadata.extract("<script>var player_x={\"url\":\"$value\",\"encrypt\":2};alert(1)</script>"))
        assertTrue(PageMediaMetadata.extract("<script>var player_x={\"url\":\"javascript:evil()\"}</script>").isEmpty())
        assertTrue(PageMediaMetadata.extract("<script>var player_x=evil()</script>").isEmpty())
    }
    @Test fun regexVerificationMatchesUpstreamThirdDetectionMode() {
        val rule=SourceRule(JSONObject("""{"name":"test","baseURL":"https://example.org","antiCrawlerConfig":{"enabled":true,"captchaDetectType":3,"captchaDetectValue":"verify-[0-9]+"}}"""))
        assertThrows(SourceVerificationRequired::class.java) { SourcePageChecks.check(rule,"<html>verify-42</html>",rule.baseUrl) }
        assertEquals("<html>result</html>",SourcePageChecks.check(rule,"<html>result</html>",rule.baseUrl))
    }
}
