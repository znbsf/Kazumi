@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package org.kazumi.tv
import android.app.Instrumentation
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import org.kazumi.tv.data.*
import org.kazumi.tv.download.*
import org.kazumi.tv.playback.*
import org.kazumi.tv.ui.*
import java.util.concurrent.atomic.AtomicBoolean

object DownloadManifestRegression {
    fun run(test:Instrumentation) {
        val context=test.targetContext
        val settings=context.getSharedPreferences("tv_settings",0).all.toMap()
        val library=context.getSharedPreferences("tv_library",0).all.toMap()
        val activity=test.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        var repository:OfflineDownloads?=null
        test.runOnMainSync { repository=OfflineDownloads.get(context) }
        val store=repository!!
        val hls=test.context.assets.open("offline-hls/index.m3u8").bufferedReader().use { it.readText() }
        val dash=test.context.assets.open("offline-dash/index.mpd").bufferedReader().use { it.readText() }
        val live=hls.replace("#EXT-X-ENDLIST","")
        val files=mutableMapOf(
            "/live.m3u8" to live.toByteArray(),
            "/master.m3u8" to "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=400000\nlive.m3u8\n".toByteArray(),
            "/drm.m3u8" to hls.replace("#EXT-X-MEDIA-SEQUENCE:0","#EXT-X-MEDIA-SEQUENCE:0\n#EXT-X-KEY:METHOD=SAMPLE-AES,KEYFORMAT=\"com.widevine\",URI=\"license\"").toByteArray(),
            "/live.mpd" to dash.replace("type=\"static\"","type=\"dynamic\"").toByteArray(),
            "/drm.mpd" to dash.replace("<Representation ","<ContentProtection schemeIdUri=\"urn:mpeg:dash:mp4protection:2011\" value=\"cenc\"/><Representation ").toByteArray(),
            "/large.m3u8" to ByteArray(DownloadManifestCheck.MAX_BYTES+1) { 'x'.code.toByte() },
            "/doctype.mpd" to dash.replace("?>","?>\n<!DOCTYPE MPD [<!ENTITY x \"fixture\">]>").toByteArray(Charsets.UTF_16),
            "/invalid.m3u8" to "not a media playlist".toByteArray()
        )
        for(name in test.context.assets.list("offline-hls-aes").orEmpty())files["/aes/$name"]=test.context.assets.open("offline-hls-aes/$name").use { it.readBytes() }
        files["/aes/master.m3u8"]="#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=400000\nindex.m3u8\n".toByteArray()
        val server=AdaptiveDownloadRegression.Server(files,0)
        server.redirects["/redirect.m3u8"]="/aes/master.m3u8"
        val ids=mutableListOf<String>(); var engine:NativePlayer?=null
        fun await(label:String,millis:Long=30000,check:()->Boolean) { val end=System.currentTimeMillis()+millis; while(System.currentTimeMillis()<end) { if(check())return; Thread.sleep(75) }; error("timeout $label") }
        fun task(id:String)=store.all().firstOrNull { it.request.id==id }
        fun enqueue(path:String):String {
            val id=DownloadMetadata.id(19000919,path); ids+=id
            check(task(id)==null)
            test.runOnMainSync { store.enqueue(PlaybackRequest("http://127.0.0.1:${server.port}$path",emptyMap(),"清单验证 · $path",path,if(path.endsWith("mpd"))"application/dash+xml" else "application/x-mpegURL"),Subject(19000919,"清单验证","",""),null) }
            return id
        }
        try {
            val cases=listOf("/live.m3u8" to DownloadFailure.LIVE,"/master.m3u8" to DownloadFailure.LIVE,"/drm.m3u8" to DownloadFailure.DRM,"/live.mpd" to DownloadFailure.LIVE,"/drm.mpd" to DownloadFailure.DRM,"/large.m3u8" to DownloadFailure.MANIFEST_LIMIT,"/invalid.m3u8" to DownloadFailure.MANIFEST_INVALID,"/doctype.mpd" to DownloadFailure.MANIFEST_INVALID)
            for((path,reason) in cases) {
                val id=enqueue(path)
                await("reject $path") { task(id)?.state==Download.STATE_FAILED }
                await("reason $path") { store.failureReason(id)==reason.label }
            }
            check(server.requests.none { it.endsWith(".ts") || it.endsWith(".m4s") || it.contains("license") })
            test.runOnMainSync { activity.setContent { KazumiTheme(false) { CompositionLocalProvider(androidx.tv.material3.LocalContentColor provides Color(0xffe4eee1)) { Box(Modifier.fillMaxSize().background(Color(0xff101412)).padding(28.dp)) { DownloadsScreen() } } } } }
            test.waitForIdleSync()
            fun visible(node:android.view.accessibility.AccessibilityNodeInfo?):Boolean {
                if(node==null)return false
                if(node.text?.toString()?.contains(DownloadFailure.MANIFEST_INVALID.label)==true)return true
                return (0 until node.childCount).any { visible(node.getChild(it)) }
            }
            await("failure label visible") { visible(test.uiAutomation.rootInActiveWindow) }
            test.waitForIdleSync(); Thread.sleep(300)
            test.uiAutomation.takeScreenshot()?.let { bitmap -> java.io.File(context.getExternalFilesDir(null),"download-manifest-errors.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle() }
            server.stallPath="/stall.m3u8"
            val stalled=enqueue("/stall.m3u8")
            await("stalled header") { server.stalled.get()>0 }
            test.runOnMainSync { store.pause(stalled) }
            await("cancel closes socket",5000) { server.stalledClosed.get()>0 && task(stalled)?.state==Download.STATE_STOPPED }
            val aes=enqueue("/aes/index.m3u8")
            await("AES complete") { task(aes)?.state==Download.STATE_COMPLETED }
            val redirected=enqueue("/redirect.m3u8")
            await("redirected master complete") { task(redirected)?.state==Download.STATE_COMPLETED }
            check(server.requests.contains("/aes/index.m3u8") && !server.requests.contains("/index.m3u8"))
            server.close()
            val rendered=AtomicBoolean()
            test.runOnMainSync {
                engine=NativePlayer(context,task(aes)!!.request.uri.toString(),offlineId=aes)
                val player=engine!!.player; player.volume=0f
                player.addListener(object:androidx.media3.common.Player.Listener { override fun onRenderedFirstFrame() { rendered.set(true) } })
                activity.setContent { PlaybackVideoSurface(player,true) }
                engine!!.open(PlaybackRequest(task(aes)!!.request.uri.toString(),emptyMap(),"AES离线夹具",mimeType="application/x-mpegURL",offlineId=aes))
            }
            await("AES offline frame") { rendered.get() }; Thread.sleep(1100)
            test.uiAutomation.takeScreenshot()?.let { bitmap -> java.io.File(context.getExternalFilesDir(null),"offline-aes-player.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle() }
            check(context.getSharedPreferences("tv_settings",0).all==settings)
            check(context.getSharedPreferences("tv_library",0).all==library)
        } catch(error:Throwable) { android.util.Log.e("DownloadManifestTest","manifest regression",error); throw error }
        finally {
            server.close()
            test.runOnMainSync { activity.setContent { }; engine?.release() }; test.waitForIdleSync()
            for(id in ids) { test.runOnMainSync { store.remove(id) }; await("cleanup") { task(id)==null }; check(store.cache.keys.none { it.startsWith("$id|") }) }
            test.runOnMainSync { activity.finish() }
        }
    }
}
