package org.kazumi.tv

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import org.kazumi.tv.data.*

class SubjectMetadataTest {
    @Test fun libraryRetainsMetadataWhileOldEntriesRemainReadable() {
        val subject=Subject(1,"title","","summary",SubjectMetadata("original","2023-09-29","TV",8.5,100,3,28))
        assertEquals(subject,LibraryCodec.subject(LibraryCodec.subjectJson(subject)))
        assertEquals(SubjectMetadata(),LibraryCodec.subject(JSONObject("""{"id":1,"title":"old"}""")).metadata)
    }
    @Test fun missingOrInvalidFactsAreNotInvented() {
        val subject = CatalogCodec.subject(JSONObject("""{"id":1,"name":"original","name_cn":null,"summary":null,"rating":{"score":11,"rank":0},"total_episodes":0}"""))
        assertEquals("original",subject.title)
        assertEquals("",subject.summary)
        assertNull(subject.metadata.score); assertNull(subject.metadata.rank); assertNull(subject.metadata.episodes)
    }
    @Test fun parsesNativeAndMirrorMetadata() {
        val subject = CatalogCodec.subject(JSONObject("""{"id":1,"name":"original","nameCN":"中文","airtime":{"date":"2023-09-29"},"image":"https://example.org/a.jpg","rating":{"score":8.5,"total":120,"rank":6},"eps":28,"total_episodes":36,"platform":"TV"}"""))
        assertEquals("中文",subject.title); assertEquals("2023-09-29",subject.metadata.date)
        assertEquals(28,subject.metadata.episodes); assertEquals(120,subject.metadata.votes)
        assertEquals(8.5,subject.metadata.score!!,0.0)
    }
    @Test fun synopsisKeepsParagraphsAndNeverRunsOrLoadsMarkup() {
        val result=SynopsisText.clean("<p>第一段&amp;内容</p><p>[b]第二段[/b]<br>下一行</p><script>bad()</script>[img]https://example.org/a.jpg[/img]")
        assertTrue(result.contains("第一段&内容\n第二段\n下一行"))
        assertFalse(result.contains("bad()")); assertFalse(result.contains("https://")); assertTrue(result.contains("[图片]"))
    }
}
