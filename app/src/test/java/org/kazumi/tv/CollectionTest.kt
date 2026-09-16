package org.kazumi.tv
import org.junit.Assert.*
import org.junit.Test
import org.kazumi.tv.data.*

class CollectionTest {
    private fun entry(id: Int,title: String="作品",time: Long=0,type: CollectionType=CollectionType.PLANNED,score: Double?=null,date: String="",original: String="") =
        CollectionEntry(Subject(id,title,"","",SubjectMetadata(originalTitle=original,date=date,score=score)),type,time)
    @Test fun oldFavoritesBecomePlannedWithoutLosingSubject() {
        val old=entry(1,original="Original")
        assertEquals(old,CollectionCodec.read(LibraryCodec.subjectJson(old.subject)))
    }
    @Test fun allLocalTypesRoundTrip() {
        CollectionType.entries.forEach { type -> val row=entry(2,type=type,time=123); assertEquals(row,CollectionCodec.read(CollectionCodec.write(row))) }
    }
    @Test fun unknownTypeIsIsolated() {
        val raw=LibraryCodec.write(listOf(CollectionCodec.write(entry(1)).put("collectionType",900),CollectionCodec.write(entry(2))))
        val read=LibraryCodec.read(raw,CollectionCodec::read)
        assertEquals(1,read.rejected); assertEquals(2,read.records.single().subject.id)
    }
    @Test fun searchMatchesAllTermsAcrossTitlesAndCase() {
        val rows=listOf(entry(1,"葬送",original="Frieren Journey"),entry(2,"葬送"))
        assertEquals(listOf(1),CollectionQuery.results(rows,query="葬送  FRIEREN").map { it.subject.id })
    }
    @Test fun invalidDatesSortWithMissingDates() {
        val rows=listOf(entry(1,date="9999-99-99"),entry(2,date="2020-02-30"),entry(3,date="2020-02-29"))
        assertEquals(listOf(3,1,2),CollectionQuery.results(rows,sort=CollectionSort.DATE).map { it.subject.id })
    }
    @Test fun filterAndDeterministicTieBreak() {
        val rows=listOf(entry(3),entry(1),entry(2,type=CollectionType.WATCHED))
        assertEquals(listOf(1,3),CollectionQuery.results(rows,CollectionType.PLANNED).map { it.subject.id })
    }
    @Test fun allSortsRespectMissingValuesAndRecentTieBreak() {
        val rows=listOf(entry(1,"B",time=1,score=8.0,date="2020-01-01"),entry(2,"A",time=2),entry(3,"C",time=3,score=9.0,date="2021-01-01"))
        assertEquals(listOf(3,2,1),CollectionQuery.results(rows).map { it.subject.id })
        assertEquals(listOf(2,1,3),CollectionQuery.results(rows,sort=CollectionSort.TITLE).map { it.subject.id })
        assertEquals(listOf(3,1,2),CollectionQuery.results(rows,sort=CollectionSort.RATING).map { it.subject.id })
        assertEquals(listOf(3,1,2),CollectionQuery.results(rows,sort=CollectionSort.DATE).map { it.subject.id })
    }
}
