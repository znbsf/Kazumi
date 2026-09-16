package org.kazumi.tv.rules

import org.junit.Assert.*
import org.junit.Test
import org.kazumi.tv.playback.MediaAddress

class MediaAddressTest {
    @Test fun detectsMediaPathWithSignedQuery() {
        assertTrue(MediaAddress.isMedia("https://example.org/live/index.m3u8?token=abc"))
        assertTrue(MediaAddress.isMedia("https://example.org/video.MP4"))
    }
    @Test fun ignoresSegmentsAndPageQueryMentions() {
        assertFalse(MediaAddress.isMedia("https://example.org/play?url=a.mp4"))
        assertFalse(MediaAddress.isMedia("https://example.org/segment.ts"))
        assertFalse(MediaAddress.isMedia("file:///video.mp4"))
    }
}
