package org.kazumi.tv

import org.junit.Assert.*
import org.junit.Test
import org.kazumi.tv.data.*
import org.kazumi.tv.playback.DanmakuTimeline

class DanmakuTest {
    @Test fun signatureIsRawSha256Base64WithPathOnly() {
        val signature = DanmakuProtocol.signature("app", 1735660800, "/api/v2/comment/123", "secret")
        assertEquals("KLk0whdohp9F1/N0/miPs64uKcsQGIZTucCaFJo6OT0=", signature)
        assertNotEquals(signature, DanmakuProtocol.signature("app", 1735660801, "/api/v2/comment/123", "secret"))
    }
    @Test(expected = IllegalArgumentException::class) fun queryCannotAccidentallyEnterSignature() {
        DanmakuProtocol.signature("app", 1, "/api/v2/comment/123?withRelated=true", "secret")
    }
    @Test fun malformedCommentsAreSkippedAndTimelineIsSorted() {
        val items = DanmakuProtocol.comments("""{"comments":[
            {"p":"2.50,5,16777215,user","m":"top"},
            {"p":"1,1,255,user","m":"scroll"},
            {"p":"NaN,1,0,user","m":"bad"},
            {"p":"-1,1,0,user","m":"bad"},
            {"p":"2,8,0,user","m":"bad"},
            {"p":"2,1,-1,user","m":"bad"},
            {"p":"1,1,0,user","m":""},
            {"p":"oops","m":"bad"}] }""")
        assertEquals(listOf(1000L, 2500L), items.map { it.timeMs })
        assertEquals(0xFF0000FF.toInt(), items[0].color)
        assertEquals(5, items[1].mode)
    }
    @Test fun seekingUsesPlaybackTimeAndDoesNotAccumulateComments() {
        val line = DanmakuTimeline(listOf(DanmakuComment(1000, 1, -1, "first"), DanmakuComment(9000, 5, -1, "second")))
        assertTrue(line.visible(0).isEmpty())
        assertEquals("first", line.visible(2000).single().comment.text)
        assertEquals("second", line.visible(10000).single().comment.text)
        assertEquals("first", line.visible(2000).single().comment.text)
        assertTrue(line.visible(8500).isEmpty())
    }
    @Test fun denseBurstCannotOverlapLanesOrExceedEightVisibleItems() {
        val burst = (1..100).map { DanmakuComment(1000, 1, -1, "$it") } +
            listOf(DanmakuComment(1000, 4, -1, "bottom"), DanmakuComment(1000, 5, -1, "top"))
        val items = DanmakuTimeline(burst).visible(2000)
        assertEquals(8, items.size)
        assertEquals(8, items.map { it.lane }.distinct().size)
    }
    @Test fun credentialsCannotBeExposedByToString() {
        assertFalse(DanmakuCredentials("id", "very-secret").toString().contains("very-secret"))
    }
}
