package org.kazumi.tv.playback

enum class MemoryMode(val label: String) {
    AUTO("自动"), LOW("低内存"), STANDARD("标准");
    companion object { fun fromStored(value: String?) = entries.firstOrNull { it.name == value } ?: AUTO }
}

/** Media3 allocator targets, not a cap on decoder, Surface or total process memory. */
data class ResourcePolicy(val lowMemory: Boolean, val local: Boolean, val metered: Boolean,
    val minBufferMs: Int, val maxBufferMs: Int, val startMs: Int, val rebufferMs: Int,
    val targetBytes: Int, val imageCacheBytes: Int) {
    val label get() = "${if (lowMemory) "低内存" else "标准"} · ${if (local) "本地" else if (metered) "计费网络" else "在线"}"
    companion object {
        fun select(mode: MemoryMode, constrainedDevice: Boolean, local: Boolean = false, metered: Boolean = false): ResourcePolicy {
            val low = mode == MemoryMode.LOW || (mode == MemoryMode.AUTO && constrainedDevice)
            val reduced = low || metered || local
            return ResourcePolicy(low, local, metered && !local,
                if (local) 2000 else if (reduced) 5000 else 15000,
                if (local) 5000 else if (reduced) 12000 else 30000,
                if (local) 250 else 1000, if (local) 500 else if (reduced) 2000 else 3000,
                (if (local) 4 else if (reduced) 8 else 16) * 1024 * 1024,
                (if (low) 6 else 12) * 1024 * 1024)
        }
    }
}
