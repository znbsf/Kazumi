package org.kazumi.tv

import org.junit.Assert.*
import org.junit.Test
import org.kazumi.tv.playback.LegacyMediaAddress

class LegacyMediaAddressTest {
    @Test fun preservesEncodedMediaQueryWithoutDecodingItsSignatureTwice() {
        assertEquals(listOf("https://cdn.example/video.m3u8?token=a%2Bb&part=2"), LegacyMediaAddress.extract(
            "https://parser.example/?url=https%3A%2F%2Fcdn.example%2Fvideo.m3u8%3Ftoken%3Da%252Bb%26part%3D2"))
    }
    @Test fun rejectsScriptsCredentialsAndNonMedia() {
        for (value in listOf("javascript:alert(1)","https://user:pass@cdn.example/a.mp4","https://cdn.example/page.html"))
            assertTrue(LegacyMediaAddress.extract("https://parser.example/?url="+java.net.URLEncoder.encode(value,"UTF-8")).isEmpty())
        assertTrue(LegacyMediaAddress.extract("invalid%").isEmpty())
    }
}
