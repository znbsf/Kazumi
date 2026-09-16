package org.kazumi.tv.domain

import org.junit.Assert.*
import org.junit.Test

class EpisodeNumberTest {
    @Test fun normalEpisodeLabels() {
        listOf("第01集", "01", "EP 1", "Episode 1", "葬送的芙莉莲 · 第1话").forEach {
            assertEquals(it, 1, EpisodeNumber.parse(it))
        }
        assertEquals(1100, EpisodeNumber.parse("第1100集"))
    }
    @Test fun ambiguousSpecialsAreNeverAutomaticallyMapped() {
        listOf("SP01", "第12.5集", "OVA1", "第一集", "2026年 第1集", "第0集", "第1-2集").forEach {
            assertNull(it, EpisodeNumber.parse(it))
        }
    }
}
