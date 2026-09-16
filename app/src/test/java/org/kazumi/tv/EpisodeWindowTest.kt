package org.kazumi.tv
import org.junit.Assert.*
import org.junit.Test
import org.kazumi.tv.domain.EpisodeWindow
class EpisodeWindowTest {
    @Test fun everyEpisodeAppearsExactlyOnceInBothOrders() {
        for(count in listOf(0,1,49,50,51,200,201,10000))for(reverse in listOf(false,true)) {
            val actual=(0 until (count+49)/50).flatMap { EpisodeWindow.indices(count,it,reverse) }
            val expected=(0 until count).toList().let { if(reverse)it.reversed() else it }
            assertEquals(expected,actual)
        }
    }
    @Test fun currentAndReturnedIndicesRemainOriginal() {
        for(index in 0 until 201)for(reverse in listOf(false,true)) {
            val page=EpisodeWindow.pageOf(index,201,reverse)
            assertTrue(index in EpisodeWindow.indices(201,page,reverse))
            assertTrue(EpisodeWindow.indices(201,page,reverse).size<=50)
        }
    }
    @Test fun locatingUsesOneBasedListPositionAndRejectsInvalidInput() {
        assertEquals(99,EpisodeWindow.locate("100",201)); assertEquals(0,EpisodeWindow.locate("1",201))
        listOf("0","202","-1","SP1","999999999999999","").forEach { assertNull(EpisodeWindow.locate(it,201)) }
    }
}
