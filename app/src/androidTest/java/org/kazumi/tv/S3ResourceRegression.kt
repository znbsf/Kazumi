package org.kazumi.tv

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import org.kazumi.tv.data.TvPreferences
import org.kazumi.tv.playback.*

object S3ResourceRegression {
    fun run(test: Instrumentation): String {
        val context = object : ContextWrapper(test.targetContext) {
            override fun getSharedPreferences(name: String, mode: Int) =
                baseContext.getSharedPreferences("s3_resource_fixture", Context.MODE_PRIVATE)
        }
        val raw = context.getSharedPreferences("", 0)
        raw.edit().clear().commit()
        try {
            val preferences = TvPreferences(context)
            check(preferences.memoryMode == MemoryMode.AUTO)
            preferences.memoryMode = MemoryMode.LOW
            check(TvPreferences(context).memoryMode == MemoryMode.LOW)
            val low = DeviceResourcePolicy.read(context, "https://example.invalid/video")
            check(low.lowMemory && low.targetBytes == 8 * 1024 * 1024 && low.imageCacheBytes == 6 * 1024 * 1024)
            check(DeviceResourcePolicy.read(context, "file:///fixture.wav").local)
            preferences.memoryMode = MemoryMode.STANDARD
            check(!DeviceResourcePolicy.read(context).lowMemory)
            raw.edit().putString("memory_mode", "unknown-version").commit()
            check(preferences.memoryMode == MemoryMode.AUTO)
            S3MediaRegression.run(test, low)
            val actual = DeviceResourcePolicy.read(test.targetContext)
            val cache = coil.Coil.imageLoader(test.targetContext).memoryCache!!
            check(cache.maxSize == actual.imageCacheBytes)
            return "preferences_reopen, invalid_mode_fallback, local_detection, low_buffer_playback_and_media_controls=OK; device=${actual.label}; image_cache_mib=${cache.maxSize / 1024 / 1024}"
        } finally { raw.edit().clear().commit() }
    }
}
