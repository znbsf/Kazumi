package org.kazumi.tv
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.kazumi.tv.data.*

class SeasonCalendarTest {
    @Test fun crossYearAndStrictWeekdays() {
        val winter=AnimeSeason(2026,1)
        assertEquals(AnimeSeason(2025,4),winter.shift(-1))
        assertEquals("2027-01-01",AnimeSeason(2026,4).end)
        assertEquals("2026-04-01",AnimeSeason(2026,2).start)
        assertEquals(winter,winter.shift(-9).shift(9))
        assertEquals(0,AnimeSeason.weekday("2026-09-14"))
        assertEquals(6,AnimeSeason.weekday("2026-09-20"))
        assertNull(AnimeSeason.weekday("2026-02-29"))
        assertNotNull(AnimeSeason.weekday("2024-02-29"))
        assertNull(AnimeSeason.weekday("2026-13-01"))
        assertNull(AnimeSeason.weekday("unknown"))
    }
    @Test fun mirrorKeepsWeekdayIdentityAndIsolatesBadRows() {
        val result=SeasonCalendar.mirror("""{"1":[],"7":[{"subject":{"id":7,"name":"Sunday","date":"2026-04-05"}},{"subject":{"id":7,"name":"duplicate"}},{"broken":1}]}""")
        assertEquals(7,result.days.size)
        assertEquals(listOf(7),result.days[6].map { it.id })
        assertTrue(result.days[0].isEmpty())
        assertTrue(runCatching { SeasonCalendar.mirror("{}") }.isFailure)
    }
    private fun row(id:Int,date:String="2026-04-06")=JSONObject().put("id",id).put("name","Show$id").put("date",date)
    @Test fun officialReadsBeyondFirstPageAndSkipsUnknownDates()=runBlocking {
        val offsets=mutableListOf<Int>()
        val result=SeasonCalendar.official { offset ->
            offsets.add(offset)
            val rows=JSONArray()
            if(offset==0)for(id in 1..20)rows.put(row(id)) else { rows.put(row(21,"invalid")); rows.put(row(22,"2026-04-05")) }
            JSONObject().put("total",22).put("data",rows).toString()
        }
        assertEquals(listOf(0,20),offsets)
        assertEquals(20,result.days[0].size)
        assertEquals(22,result.days[6].single().id)
        assertTrue(result.notice!!.contains("1 条"))
    }
    @Test fun repeatedPageStopsAndReportsIncomplete()=runBlocking {
        var calls=0
        val rows=JSONArray(); for(id in 1..20)rows.put(row(id))
        val result=SeasonCalendar.official { calls++; JSONObject().put("data",rows).put("total",1000).toString() }
        assertEquals(2,calls); assertEquals(20,result.days.sumOf { it.size })
        assertTrue(result.notice!!.contains("未全部"))
    }
    @Test fun midPageFailureNeverReturnsPartialSchedule()=runBlocking {
        val rows=JSONArray(); for(id in 1..20)rows.put(row(id))
        val result=runCatching { SeasonCalendar.official { offset -> if(offset>0)error("offline") else JSONObject().put("data",rows).toString() } }
        assertTrue(result.isFailure)
    }
}
