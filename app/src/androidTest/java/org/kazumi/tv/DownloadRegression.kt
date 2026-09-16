@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package org.kazumi.tv

import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.offline.Download
import org.kazumi.tv.download.*
import org.kazumi.tv.playback.*
import org.kazumi.tv.data.*
import org.kazumi.tv.ui.*
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

object DownloadRegression {
    private const val TEST_KEY="download-regression-20260916"
    private const val SUBJECT=19000916
    fun run(test:Instrumentation,reopen:Boolean) {
        val context=test.targetContext
        val prefs=context.getSharedPreferences("tv_settings",0).all.toMap()
        val library=context.getSharedPreferences("tv_library",0).all.toMap()
        val activity=test.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        var repository:OfflineDownloads?=null
        test.runOnMainSync { repository=OfflineDownloads.get(context) }
        val downloads=repository!!
        val id=DownloadMetadata.id(SUBJECT,TEST_KEY)
        val payload=test.context.assets.open("tracks-fixture.mp4").use { it.readBytes() }
        val userIds=downloads.all().map { it.request.id }.filter { it!=id }.toSet()
        var server:FixtureServer?=null
        var engine:NativePlayer?=null
        fun await(label:String,condition:()->Boolean) {
            val end=System.currentTimeMillis()+45000
            while(System.currentTimeMillis()<end) { if(condition())return; Thread.sleep(100) }
            error("timeout $label")
        }
        try {
            if(!reopen) {
                if(downloads.all().any { it.request.id==id }) { test.runOnMainSync { downloads.remove(id) }; await("old fixture cleanup") { downloads.all().none { it.request.id==id } } }
                server=FixtureServer(payload)
                val url="http://127.0.0.1:${server.port}/fixture.mp4"
                test.runOnMainSync { downloads.enqueue(PlaybackRequest(url,mapOf("X-Test" to "fixture"),"下载回归 · 第1集",TEST_KEY,"video/mp4"),Subject(SUBJECT,"下载回归","",""),null) }
                await("first bytes") { server!!.bytes.get()>40000 }
                test.runOnMainSync { downloads.pause(id) }
                await("stopped") { downloads.all().firstOrNull { it.request.id==id }?.state==Download.STATE_STOPPED }
                test.runOnMainSync { downloads.resume(id) }
                await("completed") { downloads.all().firstOrNull { it.request.id==id }?.state==Download.STATE_COMPLETED }
                check(server.ranges.get()>0) { "resume did not reuse partial bytes" }
                check(server.badHeaders.get()==0)
            } else check(downloads.all().firstOrNull { it.request.id==id }?.state==Download.STATE_COMPLETED)
            val task=downloads.all().first { it.request.id==id }
            server?.close(); server=null
            val source=downloads.offlineFactory(id).createDataSource()
            val bytes=java.io.ByteArrayOutputStream()
            try {
                source.open(DataSpec(Uri.parse(task.request.uri.toString()))); val buffer=ByteArray(8192)
                while(true) { val n=source.read(buffer,0,buffer.size); if(n<0)break; bytes.write(buffer,0,n) }
            } finally { source.close() }
            check(bytes.toByteArray().contentEquals(payload)) { "offline bytes differ" }
            if(reopen) {
                test.runOnMainSync {
                    engine=NativePlayer(context,task.request.uri.toString(),offlineId=id)
                    activity.setContent { androidx.compose.ui.viewinterop.AndroidView(factory={ androidx.media3.ui.PlayerView(it).apply { player=engine!!.player } }) }
                    engine!!.player.volume=0f
                    engine!!.open(PlaybackRequest(task.request.uri.toString(),emptyMap(),"离线夹具",offlineId=id))
                }
                await("offline ready") { var ready=false; test.runOnMainSync { ready=engine!!.player.playbackState==androidx.media3.common.Player.STATE_READY }; ready }
                test.runOnMainSync { check(engine!!.player.videoSize.width>0); engine!!.release(); engine=null }
            }
            test.runOnMainSync { activity.setContent { KazumiTheme(false) { CompositionLocalProvider(androidx.tv.material3.LocalContentColor provides Color(0xffe4eee1)) { Box(Modifier.fillMaxSize().background(Color(0xff101412)).padding(28.dp)) { DownloadsScreen() } } } } }
            test.waitForIdleSync(); Thread.sleep(1300)
            test.uiAutomation.takeScreenshot()?.let { bitmap -> java.io.File(context.getExternalFilesDir(null),"downloads.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle() }
            if(reopen) {
                val span=downloads.cache.keys.filter { it.startsWith("$id|") }.flatMap { downloads.cache.getCachedSpans(it) }.first()
                downloads.cache.removeSpan(span)
                val incomplete=downloads.offlineFactory(id).createDataSource()
                val missing=runCatching {
                    incomplete.open(DataSpec(task.request.uri)); val scratch=ByteArray(8192)
                    while(incomplete.read(scratch,0,scratch.size)>=0) { }
                }
                incomplete.close(); check(missing.isFailure) { "missing local data silently succeeded" }
                test.runOnMainSync { downloads.remove(id) }
                await("removed") { downloads.all().none { it.request.id==id } }
                check(downloads.cache.keys.none { it.startsWith("$id|") })
                check(runCatching { downloads.offlineFactory(id) }.isFailure)
            }
            check(downloads.all().map { it.request.id }.filter { it!=id }.toSet()==userIds)
            check(context.getSharedPreferences("tv_settings",0).all==prefs)
            check(context.getSharedPreferences("tv_library",0).all==library)
        } catch(error:Throwable) {
            android.util.Log.e("DownloadTest","regression failed: "+downloads.all().filter { it.request.id==id }.joinToString { "state=${it.state},stop=${it.stopReason},bytes=${it.bytesDownloaded}" },error); throw error
        } finally {
            server?.close()
            test.runOnMainSync { engine?.release(); activity.finish() }
        }
    }
    private class FixtureServer(private val bytesToSend:ByteArray) {
        private val server=ServerSocket(0,10,java.net.InetAddress.getByName("127.0.0.1"))
        val port=server.localPort; val bytes=AtomicInteger(); val ranges=AtomicInteger(); val badHeaders=AtomicInteger()
        @Volatile private var closed=false
        init { Thread { while(!closed) { val socket=try { server.accept() } catch(_:Exception) { break }; Thread { serve(socket) }.apply { isDaemon=true; start() } } }.apply { isDaemon=true; start() } }
        private fun serve(socket:Socket) {
            try { socket.use {
                val reader=it.getInputStream().bufferedReader(); reader.readLine(); val headers=mutableMapOf<String,String>()
                while(true) { val line=reader.readLine() ?: break; if(line.isEmpty())break; headers[line.substringBefore(':').lowercase()]=line.substringAfter(':').trim() }
                if(headers["x-test"]!="fixture")badHeaders.incrementAndGet()
                val start=headers["range"]?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                if(start>0)ranges.incrementAndGet()
                val output=it.getOutputStream()
                val response=if(start>0)"HTTP/1.1 206 Partial Content\r\nContent-Range: bytes $start-${bytesToSend.lastIndex}/${bytesToSend.size}\r\n" else "HTTP/1.1 200 OK\r\n"
                output.write((response+"Content-Type: video/mp4\r\nAccept-Ranges: bytes\r\nETag: \"fixture-v1\"\r\nContent-Length: ${bytesToSend.size-start}\r\nConnection: close\r\n\r\n").toByteArray())
                var position=start
                while(position<bytesToSend.size && !closed) { val count=minOf(1024,bytesToSend.size-position); output.write(bytesToSend,position,count); output.flush(); bytes.addAndGet(count); position+=count; Thread.sleep(25) }
            } } catch(_:Exception) { }
        }
        fun close() { closed=true; server.close() }
    }
}
