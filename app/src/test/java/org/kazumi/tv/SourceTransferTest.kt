package org.kazumi.tv
import org.junit.Test
import org.junit.Assert.*
import org.kazumi.tv.domain.PlaybackIdentity
import org.kazumi.tv.rules.Episode

class SourceTransferTest {
    private val current=Episode("第10集","https://a.invalid/current")
    @Test fun uniqueEpisodeUsesNumberNotListIndex() {
        assertEquals(1,PlaybackIdentity.matchingAcrossSources(current,listOf(Episode("第9集","a"),Episode("EP10","b"),Episode("第11集","c"))))
    }
    @Test fun duplicateNumberCannotUseCoincidentalPageIdentity() {
        assertNull(PlaybackIdentity.matchingAcrossSources(current,listOf(current,Episode("第10集","other"))))
    }
    @Test fun specialDecimalAndMissingLabelsNeverGuess() {
        for(label in listOf("SP","10.5","第十集","第2季第10集"))assertNull(PlaybackIdentity.matchingAcrossSources(Episode(label,"same"),listOf(Episode(label,"same"))))
        assertNull(PlaybackIdentity.matchingAcrossSources(current,listOf(Episode("第11集",current.pageUrl))))
    }
}
