package org.kazumi.tv
import org.junit.Assert.*
import org.junit.Test
import org.kazumi.tv.data.*
class CalendarTest {
    @Test fun weekdayIdentityBadRowsAndDuplicateSubjects() {
        val week=CalendarCodec.read("""[{"weekday":{"id":7},"items":[{"id":1,"name_cn":"节目","air_date":"2026-09-01"},{"id":1,"name":"重复"},{"bad":1}]},{"weekday":{"id":0},"items":[]}]""")
        assertEquals(7,week.days.size); assertTrue(week.days[0].isEmpty())
        assertEquals(1,week.days[6].size); assertEquals("2026-09-01",week.days[6][0].metadata.date)
    }
    @Test fun independentFiltersAndStableSorting() {
        val a=Subject(1,"A","","",SubjectMetadata(score=8.0,votes=2))
        val b=Subject(2,"B","","",SubjectMetadata(score=9.0,votes=1))
        val c=Subject(3,"C","","")
        val saved=listOf(CollectionEntry(a,CollectionType.WATCHING),CollectionEntry(b,CollectionType.WATCHED),CollectionEntry(c,CollectionType.ABANDONED))
        assertEquals(listOf(a),ScheduleQuery.filter(listOf(a,b,c),saved,true,false,false,0))
        assertEquals(listOf(a),ScheduleQuery.filter(listOf(a,b,c),saved,false,true,true,0))
        assertEquals(listOf(b,a,c),ScheduleQuery.filter(listOf(a,b,c),saved,false,false,false,1))
    }
}
