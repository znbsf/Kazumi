package org.kazumi.tv
import org.junit.Assert.*
import org.junit.Test
import org.kazumi.tv.data.*
import java.util.TimeZone
import java.text.SimpleDateFormat

class HistoryQueryTest {
    private fun time(value: String)=SimpleDateFormat("yyyy-MM-dd HH:mm").apply { timeZone=TimeZone.getTimeZone("UTC") }.parse(value)!!.time
    private fun row(key:String,id:Int=1,at:Long=0,kind:HistoryKind=HistoryKind.ONLINE)=HistoryEntry(key,Subject(id,"同名作品","",""),"第1集",1000,10000,PlaybackOrigin("Rule","","",""),at,kind)
    @Test fun dateGroupsRespectLocalMidnightAndUnknownTime() {
        val rows=listOf(row("a",at=time("2026-09-15 16:01")),row("b",at=time("2026-09-15 15:59")),row("old"))
        val groups=HistoryQuery.groups(rows,HistoryGrouping.DATE,time("2026-09-16 01:00"),TimeZone.getTimeZone("Asia/Hong_Kong"))
        assertEquals(listOf("今天","昨天","时间未知"),groups.map { it.label })
    }
    @Test fun subjectGroupingNeverMergesByTitleOrDiscardsSources() {
        val rows=listOf(row("a",at=1),row("b",at=3),row("c",id=2,at=2))
        val groups=HistoryQuery.groups(rows,HistoryGrouping.SUBJECT)
        assertEquals(2,groups.size); assertEquals(listOf("b","a"),groups.first().entries.map { it.key })
        assertEquals("c",groups.last().entries.single().key)
    }
    @Test fun filterRetainsExactIdentityAndKind() {
        val rows=listOf(row("online"),row("cached",kind=HistoryKind.OFFLINE))
        assertEquals("cached",HistoryQuery.filter(rows,"RULE",HistoryKind.OFFLINE).single().key)
        assertTrue(HistoryQuery.filter(rows,"missing").isEmpty())
    }
    @Test fun legacyKindDefaultsOnlineAndOfflineRoundTrips() {
        val entry=row("a",kind=HistoryKind.OFFLINE)
        assertEquals(entry,LibraryCodec.history(LibraryCodec.historyJson(entry)))
        assertEquals(HistoryKind.ONLINE,LibraryCodec.history(LibraryCodec.historyJson(entry).apply { remove("kind") }).kind)
    }
}
