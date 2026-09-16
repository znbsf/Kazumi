package org.kazumi.tv
import org.junit.Assert.*
import org.junit.Test
import org.kazumi.tv.download.DownloadRangePolicy as Policy
class DownloadRangePolicyTest {
    @Test fun strongOnly() {
        assertEquals("\"v1\"",Policy.strongTag("\"v1\""))
        listOf("W/\"v1\"","v1","\"bad\nvalue\"","\"a\"b\"",null).forEach { assertNull(Policy.strongTag(it)) }
    }
    @Test fun stableRange() { Policy.validate(12,true,"\"v1\"",206,"\"v1\"","bytes 12-99/100","video/mp4") }
    @Test fun refusesChangedMissingWeakAndIgnoredRange() {
        listOf("\"v2\"",null,"W/\"v1\"").forEach { tag -> assertTrue(runCatching { Policy.validate(12,true,"\"v1\"",206,tag,"bytes 12-99/100","video/mp4") }.isFailure) }
        assertTrue(runCatching { Policy.validate(12,true,"\"v1\"",200,"\"v1\"",null,"video/mp4") }.isFailure)
        assertTrue(runCatching { Policy.validate(12,true,null,206,null,"bytes 12-99/100",null) }.isFailure)
    }
    @Test fun refusesWrongRangesAndNonMedia() {
        listOf("bytes 0-99/100","bytes 12-100/100","bytes 12-10/100","bytes 12-999999999999999999999/100","invalid").forEach { range -> assertTrue(runCatching { Policy.validate(12,false,null,206,null,range,null) }.isFailure) }
        assertTrue(runCatching { Policy.validate(0,false,null,200,null,null,"text/html; charset=utf-8") }.isFailure)
        assertTrue(runCatching { Policy.validate(0,false,null,201,null,null,null) }.isFailure)
        Policy.validate(0,false,null,200,null,null,"application/vnd.apple.mpegurl")
    }
}
