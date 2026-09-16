package org.kazumi.tv
import org.junit.Test
import org.junit.Assert.*
import org.kazumi.tv.data.*

class CollectionChangesTest {
    private val a=CollectionEntry(Subject(1,"A","",""),CollectionType.PLANNED,10)
    @Test fun deletesSurviveRoundTripAndRepeatedMerge() {
        val add=CollectionChanges.append(emptyList(),emptyList(),listOf(a),10)
        val all=CollectionChanges.append(add,listOf(a),emptyList(),10)
        assertEquals(2,all.map { it.id }.distinct().size)
        assertTrue(all[1].timestamp>all[0].timestamp)
        assertEquals(all,CollectionChanges.read(CollectionChanges.write(all)))
        val merged=CollectionChanges.merge(listOf(a),add,all)
        assertTrue(merged.collections.isEmpty())
        assertEquals(merged,CollectionChanges.merge(merged.collections,merged.changes,all))
    }
    @Test fun deleteThenReAddAndUpdateMissingSemantics() {
        val add=CollectionChanges.append(emptyList(),emptyList(),listOf(a),20)
        val deleted=CollectionChanges.append(add,listOf(a),emptyList(),10)
        val next=a.copy(type=CollectionType.WATCHED)
        val readd=CollectionChanges.append(deleted,emptyList(),listOf(next),0)
        assertEquals(listOf(next),CollectionChanges.merge(listOf(a),add,readd).collections)
        val update=CollectionChange("update",1,CollectionAction.UPDATE,30,next)
        assertTrue(CollectionChanges.merge(emptyList(),emptyList(),listOf(update)).collections.isEmpty())
    }
    @Test fun conflictingIdentityAndInvalidPayloadAreRejected() {
        val change=CollectionChange("x",1,CollectionAction.ADD,1,a)
        assertTrue(runCatching { CollectionChanges.merge(listOf(a),listOf(change),listOf(change.copy(action=CollectionAction.UPDATE))) }.isFailure)
        assertTrue(runCatching { CollectionChanges.write(listOf(change,change)) }.isFailure)
        assertTrue(runCatching { CollectionChanges.write(listOf(change.copy(subjectId=2))) }.isFailure)
        assertTrue(runCatching { CollectionChanges.read("{\"version\":2,\"changes\":[]}") }.isFailure)
    }
    @Test fun sameMillisecondBatchHasStableOrderingAndNoLoss() {
        val b=a.copy(subject=a.subject.copy(id=2))
        val events=CollectionChanges.append(emptyList(),emptyList(),listOf(b,a),5)
        assertEquals(listOf(1,2),events.map { it.subjectId })
        assertEquals(2,events.map { it.id }.distinct().size)
        assertTrue(events[1].timestamp>events[0].timestamp)
        assertEquals(events,CollectionChanges.append(events,listOf(a,b),listOf(b,a),1))
    }
    @Test fun capacityFailureDoesNotTruncate() {
        val full=List(CollectionChanges.LIMIT) { CollectionChange("$it",1,CollectionAction.DELETE,it.toLong(),null) }
        assertTrue(runCatching { CollectionChanges.append(full,emptyList(),listOf(a),0) }.isFailure)
        assertEquals(CollectionChanges.LIMIT,full.size)
        val remote=List(200) { a.copy(subject=a.subject.copy(id=it+1)) }
        val extra=a.copy(subject=a.subject.copy(id=201))
        assertTrue(runCatching { CollectionChanges.merge(remote,emptyList(),listOf(CollectionChange("extra",201,CollectionAction.ADD,1,extra))) }.isFailure)
    }
}
