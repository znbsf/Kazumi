package org.kazumi.tv

import org.junit.Assert.*
import org.junit.Test
import org.kazumi.tv.playback.VideoOutput

class VideoOutputTest {
    @Test fun automaticWorkaroundIsLimitedToVerifiedDeviceAndAndroidVersion() {
        assertTrue(VideoOutput.AUTO.usesTexture("mulan",28))
        assertFalse(VideoOutput.AUTO.usesTexture("mulan",29))
        assertFalse(VideoOutput.AUTO.usesTexture("another-tv",28))
    }
    @Test fun userCanOverrideEitherDirectionAndUnknownValuesFallBack() {
        assertFalse(VideoOutput.SURFACE.usesTexture("mulan",28))
        assertTrue(VideoOutput.TEXTURE.usesTexture("another-tv",30))
        assertEquals(VideoOutput.AUTO,VideoOutput.fromStored("invalid"))
    }
}
