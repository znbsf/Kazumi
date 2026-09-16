package org.kazumi.tv
import org.junit.Test
import org.junit.Assert.*
import org.kazumi.tv.data.*

class WebDavCollectionsTest {
    @Test fun nativePathIsIsolatedAndCredentialsDoNotEnterUrl() {
        val expected="https://example.com/dav/kazumitv-collections-v1.json"
        assertEquals(expected,WebDavCollections.endpoint("https://example.com/dav").toString())
        assertEquals(expected,WebDavCollections.endpoint("https://example.com/dav/").toString())
        listOf("http://example.com/dav","https://user:pass@example.com/dav","https://example.com/dav?q=1","https://example.com/dav#x").forEach {
            assertTrue(runCatching { WebDavCollections.endpoint(it) }.isFailure)
        }
        assertEquals("WebDavAccount(redacted)",WebDavAccount("private","user","password").toString())
    }
    @Test fun onlySingleStrongTagsAreAccepted() {
        assertTrue(WebDavCollections.strongTag("\"revision-1\""))
        assertTrue(WebDavCollections.strongTag("\"\""))
        listOf(null,"W/\"1\"","*","1","\"a\", \"b\"","\"a\nb\"").forEach { assertFalse(WebDavCollections.strongTag(it)) }
    }
    @Test fun snapshotRetainsDeletionJournalAndRejectsOtherFormats() {
        val entry=CollectionEntry(Subject(1,"A","",""),CollectionType.WATCHED,9)
        val events=CollectionChanges.append(emptyList(),listOf(entry),emptyList(),10)
        val snapshot=CollectionSnapshot(emptyList(),events)
        assertEquals(snapshot,CollectionSnapshotCodec.read(BoundedText.read(CollectionSnapshotCodec.write(snapshot).reader(),CollectionSnapshotCodec.MAX_CHARS)))
        assertTrue(runCatching { CollectionSnapshotCodec.read("{\"format\":\"KazumiTV-library\",\"version\":1}") }.isFailure)
        val wrong=org.json.JSONObject(CollectionSnapshotCodec.write(snapshot)).put("version",2)
        assertTrue(runCatching { CollectionSnapshotCodec.read(wrong.toString()) }.isFailure)
    }
}
