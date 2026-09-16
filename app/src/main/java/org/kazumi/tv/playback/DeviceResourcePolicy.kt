package org.kazumi.tv.playback

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import org.kazumi.tv.data.TvPreferences

object DeviceResourcePolicy {
    fun read(context: Context, uri: String? = null): ResourcePolicy {
        val memory = context.getSystemService(ActivityManager::class.java)
        val local = uri?.substringBefore(':')?.lowercase() in listOf("file", "content", "android.resource")
        val metered = runCatching { context.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered }.getOrDefault(true)
        return ResourcePolicy.select(TvPreferences(context).memoryMode,
            memory.isLowRamDevice || memory.memoryClass <= 128, local, metered)
    }
}
