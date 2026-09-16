package org.kazumi.tv

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import org.kazumi.tv.data.*
import org.kazumi.tv.rules.Episode
import org.kazumi.tv.domain.PlaybackIdentity

class LibraryCodecTest {
    private val entry = HistoryEntry("rule|https://example.org/episode/2", Subject(5,"作品","",""), "第02集", 12345, 900000,
        PlaybackOrigin("rule", "作品", "https://example.org/title/5", "线路一"))
    @Test fun originalHistoryArrayRemainsReadable() {
        val old = LibraryCodec.historyJson(entry.copy(origin = null)).apply { remove("updatedAt") }
        val read = LibraryCodec.read(JSONArray().put(old).toString(), LibraryCodec::history)
        assertEquals(12345L, read.records.single().position)
        assertNull(read.records.single().origin)
        assertFalse(read.damagedContainer)
    }
    @Test fun versionedContextRoundTripDoesNotContainResolvedRequest() {
        val raw = LibraryCodec.write(listOf(LibraryCodec.historyJson(entry)))
        assertEquals(entry, LibraryCodec.read(raw, LibraryCodec::history).records.single())
        assertFalse(raw.contains("headers")); assertFalse(raw.contains("mediaUrl"))
    }
    @Test fun invalidRowDoesNotDiscardValidNeighbors() {
        val raw = JSONArray().put(LibraryCodec.historyJson(entry)).put(JSONObject().put("id", "bad"))
            .put("not an object").put(LibraryCodec.historyJson(entry.copy(key = "another"))).toString()
        val read = LibraryCodec.read(raw, LibraryCodec::history)
        assertEquals(2, read.records.size); assertEquals(2, read.rejected)
    }
    @Test fun truncatedAndFutureFormatsAreExplicitlyDamaged() {
        assertTrue(LibraryCodec.read("[{", LibraryCodec::history).damagedContainer)
        assertTrue(LibraryCodec.read("{\"version\":2,\"records\":[]}", LibraryCodec::history).damagedContainer)
    }
    @Test fun routeMatchingNeverUsesPositionOrSpecialNumberGuess() {
        val normal = Episode("第02集", "old")
        assertEquals(0, PlaybackIdentity.matchingEpisode(normal, listOf(Episode("EP2","new"), Episode("EP1","other"))))
        assertNull(PlaybackIdentity.matchingEpisode(normal, listOf(Episode("EP2","a"), Episode("第2集","b"))))
        assertNull(PlaybackIdentity.matchingEpisode(Episode("SP2","old"), listOf(Episode("第2集","new"))))
        assertNull(PlaybackIdentity.matchingEpisode(normal, listOf(Episode("第1集","new"))))
        assertEquals(0, PlaybackIdentity.matchingEpisode(Episode("SP2","same"), listOf(Episode("特别篇","same"))))
    }
}
