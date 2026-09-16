package org.kazumi.tv

import org.junit.Assert.*
import org.junit.Test
import org.kazumi.tv.playback.*

class ResourcePolicyTest {
    @Test fun `automatic respects constrained device while explicit standard overrides it`() {
        assertTrue(ResourcePolicy.select(MemoryMode.AUTO, true).lowMemory)
        assertFalse(ResourcePolicy.select(MemoryMode.STANDARD, true).lowMemory)
        assertTrue(ResourcePolicy.select(MemoryMode.LOW, false).lowMemory)
        assertEquals(MemoryMode.AUTO, MemoryMode.fromStored("damaged"))
    }
    @Test fun `metered network limits prefetch even with standard memory choice`() {
        val online = ResourcePolicy.select(MemoryMode.STANDARD, false)
        val metered = ResourcePolicy.select(MemoryMode.STANDARD, false, metered = true)
        assertTrue(metered.targetBytes < online.targetBytes)
        assertTrue(metered.maxBufferMs < online.maxBufferMs)
        assertEquals(online.imageCacheBytes, metered.imageCacheBytes)
    }
    @Test fun `local mode never inherits network label and all profiles have valid thresholds`() {
        for (mode in MemoryMode.entries) for (constrained in listOf(false, true))
            for (local in listOf(false, true)) for (metered in listOf(false, true)) {
                val p = ResourcePolicy.select(mode, constrained, local, metered)
                assertTrue(p.startMs <= p.minBufferMs)
                assertTrue(p.rebufferMs <= p.minBufferMs)
                assertTrue(p.minBufferMs <= p.maxBufferMs)
                assertTrue(p.targetBytes > 0)
                if (local) assertFalse(p.metered)
            }
    }
}
