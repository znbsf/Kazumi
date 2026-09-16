package org.kazumi.tv
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.kazumi.tv.data.*
class SubjectRelationsTest {
    private fun row(id:Int,kind:String)=SubjectRelation(kind,Subject(id,"Show$id","",""))
    @Test fun decodeFiltersNonAnimeAndMalformedRows() {
        val rows=SubjectRelations.decode("""[{"id":2,"type":2,"name":"anime","relation":"续集"},{"id":2,"type":2,"name":"duplicate"},{"id":3,"type":1,"name":"book"},{"type":2}]""")
        assertEquals(listOf(2),rows.map { it.subject.id }); assertEquals("续集",rows.single().relation)
    }
    @Test fun ordersChainAndDoesNotFollowSideStories()=runBlocking {
        val fetched=mutableListOf<Int>()
        val graph=mapOf(3 to listOf(row(2,"前传"),row(4,"续集"),row(9,"番外篇")),2 to listOf(row(1,"前传"),row(3,"续集")),4 to listOf(row(5,"续集"),row(3,"前传")))
        val result=SubjectRelations.resolve(3) { fetched.add(it); graph[it].orEmpty() }
        assertEquals(listOf(1,2,4,5,9),result.items.map { it.subject.id })
        assertFalse(9 in fetched); assertFalse(result.truncated)
    }
    @Test fun cyclesAreFetchedOnceAndRootIsExcluded()=runBlocking {
        val fetched=mutableListOf<Int>()
        val result=SubjectRelations.resolve(1) { fetched.add(it); when(it) { 1 -> listOf(row(2,"续集")); 2 -> listOf(row(3,"续集")); else -> listOf(row(2,"续集"),row(1,"续集")) } }
        assertEquals(listOf(1,2,3),fetched); assertEquals(listOf(2,3),result.items.map { it.subject.id })
    }
    @Test fun countAndDepthLimitsReportPartial()=runBlocking {
        var count=0
        val result=SubjectRelations.resolve(1,maxFetch=2) { count++; listOf(row(it+1,"续集")) }
        assertEquals(2,count); assertTrue(result.truncated)
        val depth=SubjectRelations.resolve(1,maxDepth=1) { listOf(row(it+1,"前传")) }
        assertTrue(depth.truncated); assertEquals(listOf(2),depth.items.map { it.subject.id })
    }
    @Test fun failureIsNotSilentlyReturnedAsFullChain()=runBlocking {
        assertTrue(runCatching { SubjectRelations.resolve(1) { if(it==2)error("offline") else listOf(row(2,"续集")) } }.isFailure)
    }
}
