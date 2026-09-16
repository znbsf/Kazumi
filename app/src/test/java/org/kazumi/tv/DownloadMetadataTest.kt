package org.kazumi.tv
import org.junit.Test
import org.junit.Assert.*
import org.kazumi.tv.download.DownloadMetadata
import org.kazumi.tv.data.*
class DownloadMetadataTest {
    @Test fun preservesIdentityOriginAndRequestHeaders() {
        val subject=Subject(1,"标题","","简介")
        val origin=PlaybackOrigin("规则","来源","https://example.test/page","线路")
        val value=DownloadMetadata.read(DownloadMetadata(subject,"第10集","rule|page",mapOf("Referer" to "https://example.test/"),origin).bytes())
        assertEquals(subject,value.subject); assertEquals(origin,value.origin)
        assertEquals("rule|page",value.resumeKey); assertEquals("https://example.test/",value.headers["Referer"])
    }
    @Test fun separatesSourcesAndSubjects() {
        val first=DownloadMetadata.id(1,"a|ep")
        assertEquals(first,DownloadMetadata.id(1,"a|ep"))
        assertNotEquals(first,DownloadMetadata.id(1,"b|ep"))
        assertNotEquals(first,DownloadMetadata.id(2,"a|ep"))
        assertTrue(first.matches(Regex("[0-9a-f]{64}")))
    }
    @Test fun rejectsFutureAndOversizedRecords() {
        assertTrue(runCatching { DownloadMetadata.read("{\"version\":99}".toByteArray()) }.isFailure)
        assertTrue(runCatching { DownloadMetadata.read(ByteArray(128*1024+1)) }.isFailure)
    }
}
