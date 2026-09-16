package org.kazumi.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.kazumi.tv.ui.TvApp
import coil.Coil
import coil.ImageLoader
import coil.memory.MemoryCache
import coil.disk.DiskCache

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TvApp() }
    }
}

/** One image loader and disk cache per process, including Activity recreation. */
class KazumiApplication : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        org.kazumi.tv.data.NetworkSettings.initialize(this)
        // Debug deployment imports from the app-private directory, then removes the plaintext handoff.
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            val incoming = filesDir.resolve("danmaku-import.json")
            if (incoming.exists()) {
                try {
                    check(incoming.length() <= 4096)
                    val json = org.json.JSONObject(incoming.readText())
                    org.kazumi.tv.data.DanmakuCredentialStore(this).save(org.kazumi.tv.data.DanmakuCredentials(json.getString("appId"), json.getString("appSecret")))
                    android.util.Log.i("KazumiDanmaku", "credentials_imported")
                } catch (_: Exception) { android.util.Log.w("KazumiDanmaku", "credentials_import_failed") }
                finally { incoming.delete() }
            }
        }
        Coil.setImageLoader(ImageLoader.Builder(applicationContext)
            .okHttpClient { org.kazumi.tv.data.AppHttp.client.newBuilder().addInterceptor { chain ->
                val request = chain.request()
                val url = request.url
                val target = org.kazumi.tv.data.CoverRouting.target(url,org.kazumi.tv.data.NetworkSettings.catalogMirror)
                chain.proceed(request.newBuilder().url(target).build())
            }.build() }
            .allowHardware(false)
            .eventListener(object : coil.EventListener {
              override fun onError(request: coil.request.ImageRequest, result: coil.request.ErrorResult) {
                val host = runCatching { java.net.URI(request.data.toString()).host }.getOrNull() ?: "unknown"
                android.util.Log.w("KazumiNetwork", "cover host=$host error=${result.throwable.javaClass.simpleName} cause=${result.throwable.cause?.javaClass?.simpleName}")
              }
            })
            .memoryCache { MemoryCache.Builder(applicationContext).maxSizeBytes(org.kazumi.tv.playback.DeviceResourcePolicy.read(this).imageCacheBytes).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("covers")).maxSizeBytes(64L * 1024 * 1024).build() }.build())
    }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) Coil.imageLoader(this).memoryCache?.clear()
    }
}
