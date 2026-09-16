package org.kazumi.tv
import org.junit.Assert.*
import org.junit.Test
import org.kazumi.tv.data.*
class LibraryArchiveTest {
    private val subject=Subject(1,"节目","","简介")
    @Test fun roundTripRetainsCategoryAndPlaybackOrigin() {
        val original=LibraryArchive(listOf(CollectionEntry(subject,CollectionType.ON_HOLD,123)),listOf(HistoryEntry("rule|page",subject,"第10集",65000,100000,PlaybackOrigin("rule","source","https://example.com/show","road"),321)),456)
        assertEquals(original,LibraryArchiveCodec.read(LibraryArchiveCodec.write(original)))
    }
    @Test fun invalidVersionOrBadRowRejectsWholeArchive() {
        val raw=LibraryArchiveCodec.write(LibraryArchive(emptyList(),emptyList(),0))
        val root=org.json.JSONObject(raw).put("version",2)
        assertTrue(runCatching { LibraryArchiveCodec.read(root.toString()) }.isFailure)
        root.put("version",1).put("history",org.json.JSONArray().put(org.json.JSONObject().put("key","broken")))
        assertTrue(runCatching { LibraryArchiveCodec.read(root.toString()) }.isFailure)
    }
    @Test fun duplicatesAndOverCapacityCannotEvictExistingRecords() {
        val row=CollectionEntry(subject,CollectionType.WATCHING)
        assertTrue(runCatching { LibraryArchiveCodec.write(LibraryArchive(listOf(row,row),emptyList(),0)) }.isFailure)
        assertTrue(runCatching { LibraryArchiveCodec.write(LibraryArchive((1..201).map { row.copy(subject=subject.copy(id=it)) },emptyList(),0)) }.isFailure)
    }
    @Test fun fingerprintHasUnambiguousFieldBoundaries() { assertNotEquals(LibraryArchiveCodec.fingerprint("a","bc"),LibraryArchiveCodec.fingerprint("ab","c")) }
}
