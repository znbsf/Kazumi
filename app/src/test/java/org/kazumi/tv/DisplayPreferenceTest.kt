package org.kazumi.tv
import org.junit.Test
import org.junit.Assert.*
import org.kazumi.tv.playback.*
class DisplayPreferenceTest {
    @Test fun stablePreferenceRetainsResolutionAndFractionalRate() {
        val fractional=DisplayPreference.from(1920,1080,59.94f)
        assertEquals(fractional,DisplayPreference.read(fractional.key))
        assertEquals("1920 × 1080 · 59.94 Hz",fractional.label)
        assertEquals(DisplayPreference.from(1920,1080,60f),DisplayPreference.from(1920,1080,60.000004f))
        assertNotEquals(fractional,DisplayPreference.from(1920,1080,60f))
    }
    @Test fun modeIdsAreReboundAndAbsentModesFallBackWithoutGuessing() {
        val chosen=DisplayPreference.from(1920,1080,60f)
        assertEquals(9,DisplayModePolicy.requestedId(chosen,listOf(AvailableDisplayMode(9,chosen))))
        assertEquals(0,DisplayModePolicy.requestedId(chosen,listOf(AvailableDisplayMode(1,chosen.copy(width=3840)))))
        assertEquals(0,DisplayModePolicy.requestedId(null,listOf(AvailableDisplayMode(9,chosen))))
        assertEquals(2,DisplayModePolicy.requestedId(chosen,listOf(AvailableDisplayMode(9,chosen),AvailableDisplayMode(2,chosen))))
    }
    @Test fun damagedUnknownAndNonFiniteValuesAreRejected() {
        listOf(null,"","v2:1920:1080:60000","v1:0:1080:60000","v1:1920:1080:0","v1:1920:1080:60000:extra").forEach { assertNull(DisplayPreference.read(it)) }
        assertTrue(runCatching { DisplayPreference.from(1920,1080,Float.NaN) }.isFailure)
        assertTrue(runCatching { DisplayPreference.from(1920,1080,Float.POSITIVE_INFINITY) }.isFailure)
    }
    @Test fun trialExpiresAtDeadlineIncludingLongSchedulingGap() {
        val trial=DisplayTrial(null,16000)
        assertEquals(15L,trial.remainingSeconds(1000)); assertEquals(1L,trial.remainingSeconds(15999))
        assertFalse(trial.expired(15999)); assertTrue(trial.expired(16000)); assertTrue(trial.expired(90000))
        assertEquals(0L,trial.remainingSeconds(90000))
    }
}
