package org.kazumi.tv

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.kazumi.tv.data.*

class SearchPagerTest {
    private fun page(offset: Int)=List(20) { Subject(offset+it+1,"result${offset+it}","","") }
    private suspend fun settled(pager: SearchPager) { withTimeout(3000) { while(pager.state.value.loading)delay(5) } }
    @Test fun lateCancelledRequestCannotReplaceNewSearch() = runBlocking {
        val pager=SearchPager(this) { query,_,_ ->
            if(query=="old")withContext(NonCancellable) { delay(120) }
            listOf(Subject(if(query=="old")1 else 2,query,"",""))
        }
        pager.submit("old"); yield(); pager.submit("new"); settled(pager); delay(180)
        assertEquals("new",pager.state.value.items.single().title)
    }
    @Test fun failureRetainsExistingPageAndRetriesSameOffset() = runBlocking {
        var fail=true
        val pager=SearchPager(this) { _,offset,_ -> if(offset==20 && fail) { fail=false; error("offline") }; page(offset) }
        pager.submit("test"); settled(pager); pager.next(); settled(pager)
        assertEquals(20,pager.state.value.failedOffset); assertEquals(20,pager.state.value.items.size)
        pager.retry(); settled(pager)
        assertEquals(40,pager.state.value.items.size); assertNull(pager.state.value.failedOffset)
    }
    @Test fun longBrowsingIsBoundedAndEarlierPageCanBeRecovered() = runBlocking {
        val pager=SearchPager(this) { _,offset,_ -> page(offset) }
        pager.submit("test"); settled(pager)
        repeat(6) { pager.next(); settled(pager) }
        assertEquals(100,pager.state.value.items.size); assertEquals(40,pager.state.value.firstOffset)
        pager.previous(); settled(pager)
        assertEquals(20,pager.state.value.firstOffset); assertEquals(100,pager.state.value.items.size)
        assertEquals(20,pager.pageFor(21))
    }
    @Test fun restoredPageAndSortUseServerOffsetsAndEmptyQueryClearsState() = runBlocking {
        val pager=SearchPager(this) { query,offset,sort -> assertEquals("saved",query); assertEquals("score",sort); page(offset).take(3) }
        pager.submit("saved","score",60); settled(pager)
        assertEquals(60,pager.state.value.firstOffset); assertTrue(pager.state.value.endReached)
        pager.submit("")
        assertTrue(pager.state.value.items.isEmpty()); assertFalse(pager.state.value.loading)
    }
    @Test fun serverIgnoringOffsetStopsAutomaticPagination() = runBlocking {
        var calls=0
        val pager=SearchPager(this) { _,_,_ -> calls++; page(0) }
        pager.submit("test"); settled(pager); pager.next(); settled(pager); pager.next(); settled(pager)
        assertEquals(2,calls); assertEquals(20,pager.state.value.failedOffset)
        assertEquals(20,pager.state.value.items.size)
    }
}
