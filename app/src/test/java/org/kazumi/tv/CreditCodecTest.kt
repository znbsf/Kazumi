package org.kazumi.tv
import org.junit.Assert.*
import org.junit.Test
import org.kazumi.tv.data.*
class CreditCodecTest {
    @Test fun allTermsMatchAcrossNameJobAndActor() {
        val actor=CreditEntry(9,"Alice","",job="配音")
        val character=CreditEntry(1,"Hero","",job="主角",character=true,actors=listOf(actor))
        val staff=CreditEntry(2,"Alice","",job="导演")
        assertEquals(listOf(character),CreditQuery.filter(listOf(character,staff),"alice 主角"))
        assertEquals(listOf(staff),CreditQuery.filter(listOf(character,staff),"导演"))
        assertTrue(CreditQuery.filter(listOf(character,staff),"missing").isEmpty())
    }

    @Test fun preservesSeparateJobsAndEpisodeCredits() {
        val rows=CreditCodec.read("""[{"id":1,"name":"A","relation":"导演","eps":"1"},{"id":1,"name":"A","relation":"原画","eps":"1"},{"id":1,"name":"A","relation":"原画","eps":"2"},{"id":1,"name":"A","relation":"原画","eps":"2"}]""",false)
        assertEquals(3,rows.size); assertEquals(3,rows.map { it.key }.distinct().size)
    }
    @Test fun malformedRowsAndActorsDoNotDiscardGoodRoles() {
        val rows=CreditCodec.read("""[{"id":2,"name":"角色","actors":[{"id":3,"name":"声优"},{"bad":1},{"id":3,"name":"重复"}]},{"name":"missing id"},{"id":-1,"name":"bad"}]""",true)
        assertEquals(1,rows.size); assertEquals(listOf(3),rows.single().actors.map { it.id })
        assertTrue(rows.single().character); assertFalse(rows.single().actors.single().character)
    }
    @Test fun nullFieldsAndUnsafeImagesHaveExplicitEmptyValues() {
        val row=CreditCodec.read("""[{"id":1,"name":"A","summary":null,"images":{"large":"file:///secret"},"actors":null}]""",false).single()
        assertEquals("",row.image); assertEquals("",row.summary); assertTrue(row.actors.isEmpty())
    }
}
