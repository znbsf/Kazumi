package org.kazumi.tv
import org.junit.Assert.*
import org.junit.Test
import org.kazumi.tv.download.*
import org.kazumi.tv.data.*
class DownloadReplacementTest {
    private val media=ReplacementMedia(DownloadMetadata.id(1,"rule|page"),"https://example.test/new.mp4",null,DownloadMetadata(Subject(1,"节目","",""),"第1集","rule|page",mapOf("Cookie" to "local-test"),null).bytes().toString(Charsets.UTF_8))
    @Test fun durableRoundTripAndStrictVersion() {
        val plan=DownloadReplacement(media,ReplacementStage.ADD)
        assertEquals(plan,DownloadReplacement.read(plan.json()))
        assertTrue(runCatching { DownloadReplacement.read(plan.json().replace("\"version\":1","\"version\":2")) }.isFailure)
        assertTrue(runCatching { DownloadReplacement.read(plan.copy(media=media.copy(id="a".repeat(64))).json()) }.isFailure)
        assertTrue(runCatching { DownloadReplacement.read(plan.copy(media=media.copy(url="https://secret@example.test/a")).json()) }.isFailure)
    }
    @Test fun removeMustFinishBeforeAdd() {
        val plan=DownloadReplacement(media)
        assertEquals(ReplacementAction.REMOVE,ReplacementRecovery.next(plan,media.copy(url="https://example.test/old.mp4"),false))
        assertEquals(ReplacementAction.WAIT,ReplacementRecovery.next(plan,media,true))
        assertEquals(ReplacementAction.STAGE_ADD,ReplacementRecovery.next(plan,null,false))
    }
    @Test fun addRecoveryIsIdempotentAndDoesNotOverwriteOtherRequest() {
        val plan=DownloadReplacement(media,ReplacementStage.ADD)
        assertEquals(ReplacementAction.ADD,ReplacementRecovery.next(plan,null,false))
        assertEquals(ReplacementAction.FINISH,ReplacementRecovery.next(plan,media,false))
        assertEquals(ReplacementAction.CONFLICT,ReplacementRecovery.next(plan,media.copy(url="https://example.test/other.mp4"),false))
        assertEquals(ReplacementAction.WAIT,ReplacementRecovery.next(plan,media,true))
    }
}
