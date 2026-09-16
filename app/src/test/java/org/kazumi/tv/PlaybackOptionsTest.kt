package org.kazumi.tv
import org.junit.Assert.*
import org.junit.Test
import org.kazumi.tv.playback.*
class PlaybackOptionsTest {
    @Test fun invalidSpeedFallsBack() {
        listOf(Float.NaN,Float.POSITIVE_INFINITY,-1f,0f,20f).forEach { assertEquals(1f,PlaybackOptions.speed(it)) }
        PlaybackOptions.speeds.forEach { assertEquals(it,PlaybackOptions.speed(it)) }
    }
    @Test fun languageIntentIsNormalizedNotAnIndex() {
        assertEquals("zh-hans",PlaybackOptions.language(" ZH_Hans "))
        assertEquals("jpn",PlaybackOptions.language("jpn"))
        listOf(null,"","und","unknown","1","track 2","<script>").forEach { assertNull(PlaybackOptions.language(it)) }
    }
    @Test fun languageAvailabilityMatchesIsoCodesButNotUnrelatedTracks() {
        assertTrue(PlaybackOptions.sameLanguage("ja","jpn"))
        assertTrue(PlaybackOptions.sameLanguage("en-US","eng"))
        assertFalse(PlaybackOptions.sameLanguage("ko","ja"))
        assertFalse(PlaybackOptions.sameLanguage(null,"und"))
    }
    @Test fun fourThreeFitsWithinBothScreenOrientations() {
        assertEquals(1440f to 1080f,PlaybackOptions.frame(1920f,1080f,PictureMode.FOUR_THREE))
        assertEquals(600f to 450f,PlaybackOptions.frame(600f,1000f,PictureMode.FOUR_THREE))
        assertEquals(1920f to 1080f,PlaybackOptions.frame(1920f,1080f,PictureMode.FIT))
        assertEquals(PictureMode.FIT,PictureMode.read("future"))
    }
}
