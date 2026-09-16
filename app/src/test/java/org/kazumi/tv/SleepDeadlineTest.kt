package org.kazumi.tv
import org.junit.Assert.*
import org.junit.Test
import org.kazumi.tv.playback.SleepDeadline
class SleepDeadlineTest {
    @Test fun expiresOnceAtDeadlineEvenAfterLongGap() {
        val timer=SleepDeadline(); timer.start(100,1000)
        assertFalse(timer.refresh(1099)); assertEquals(1L,timer.status.remainingMs)
        assertTrue(timer.refresh(10000)); assertTrue(timer.status.expired)
        assertFalse(timer.refresh(20000)); assertTrue(timer.status.expired)
    }
    @Test fun cancelAndReplacementInvalidateEarlierDeadline() {
        val timer=SleepDeadline(); timer.start(0,1000); timer.start(900,2000)
        assertFalse(timer.refresh(1000)); assertEquals(1900L,timer.status.remainingMs)
        timer.cancel(); assertFalse(timer.refresh(10000)); assertFalse(timer.status.expired)
    }
    @Test fun durationBoundsAndRepeatState() {
        val timer=SleepDeadline()
        assertTrue(runCatching { timer.start(0,0) }.isFailure)
        assertTrue(runCatching { timer.start(0,86400001) }.isFailure)
        timer.start(0,60000); timer.refresh(60000); assertEquals(60000L,timer.status.lastDurationMs)
        timer.start(60000,timer.status.lastDurationMs); assertFalse(timer.status.expired)
    }
}
