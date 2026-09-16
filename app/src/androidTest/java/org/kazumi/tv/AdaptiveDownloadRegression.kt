@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package org.kazumi.tv

import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.offline.Download
import org.kazumi.tv.download.*
import org.kazumi.tv.playback.*
import org.kazumi.tv.data.*
import org.kazumi.tv.ui.*
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

object AdaptiveDownloadRegression {
    private const val SUBJECT=19000917
    fun run(test:Instrumentation,mode:String) {
        val context=test.targetContext
        val settings=context.getSharedPreferences("tv_settings",0).all.toMap()
        val library=context.getSharedPreferences("tv_library",0).all.toMap()
        val fixtureState=context.getSharedPreferences("download_regression_phase",0)
        val activity=test.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        var repository:OfflineDownloads?=null
        test.runOnMainSync { repository=OfflineDownloads.get(context) }
        val store=repository!!
        val files=mutableMapOf<String,ByteArray>()
        files["/video.mp4"]=test.context.assets.open("tracks-fixture.mp4").use { it.readBytes() }
        for(folder in listOf("offline-hls","offline-dash"))for(name in test.context.assets.list(folder).orEmpty())files["/$folder/$name"]=test.context.assets.open("$folder/$name").use { it.readBytes() }
        val resumePhase=mode=="downloads-resume-restart"
        val server=Server(files,if(resumePhase)fixtureState.getInt("port",0) else 0)
        val owned=mutableSetOf<String>()
        var engine:NativePlayer?=null
        fun await(label:String,check:()->Boolean) { val end=System.currentTimeMillis()+45000; while(System.currentTimeMillis()<end) { if(check())return; Thread.sleep(75) }; error("timeout $label") }
        fun task(id:String)=store.all().firstOrNull { it.request.id==id }
        fun enqueue(key:String,path:String,mime:String):String {
            val id=DownloadMetadata.id(SUBJECT,key); owned+=id
            if(task(id)!=null) { test.runOnMainSync { store.remove(id) }; await("old fixture remove") { task(id)==null } }
            test.runOnMainSync { store.enqueue(PlaybackRequest("http://127.0.0.1:${server.port}$path",emptyMap(),"下载验证 · $key",key,mime),Subject(SUBJECT,"分段下载验证","",""),null) }
            return id
        }
        fun complete(id:String) { await("complete") { if(task(id)?.state==Download.STATE_FAILED)error("download failed"); task(id)?.state==Download.STATE_COMPLETED } }
        fun read(id:String,path:String):ByteArray {
            val source=store.offlineFactory(id).createDataSource(); val output=java.io.ByteArrayOutputStream()
            try { source.open(DataSpec(Uri.parse("http://127.0.0.1:${server.port}$path"))); val buffer=ByteArray(8192); while(true) { val n=source.read(buffer,0,buffer.size); if(n<0)break; output.write(buffer,0,n) } } finally { source.close() }
            return output.toByteArray()
        }
        try {
            if(mode=="downloads-pause-restart") {
                server.slow=true
                val id=enqueue("restart","/video.mp4","video/mp4")
                await("partial") { server.transferred.get()>20000 }
                test.runOnMainSync { store.pause(id) }
                await("pause") { task(id)?.state==Download.STATE_STOPPED }
                check(store.cache.keys.filter { it.startsWith("$id|") }.sumOf { key -> store.cache.getCachedSpans(key).sumOf { it.length } } in 1 until files.getValue("/video.mp4").size.toLong())
                check(fixtureState.edit().putInt("pid",android.os.Process.myPid()).putInt("port",server.port).commit())
                owned.clear() // Deliberately retained for next process.
            } else if(resumePhase) {
                val id=DownloadMetadata.id(SUBJECT,"restart"); owned+=id
                check(fixtureState.getInt("pid",0)!=android.os.Process.myPid())
                check(task(id)?.state==Download.STATE_STOPPED)
                test.runOnMainSync { store.resume(id) }; complete(id)
                check(server.conditional.get()>0 && server.ranges.get()>0)
                server.close(); check(read(id,"/video.mp4").contentEquals(files.getValue("/video.mp4")))
                fixtureState.edit().clear().commit()
            } else {
                server.slow=true
                val changed=enqueue("changed","/video.mp4","video/mp4")
                await("partial change") { server.transferred.get()>20000 }
                test.runOnMainSync { store.pause(changed) }; await("paused change") { task(changed)?.state==Download.STATE_STOPPED }
                server.tag="\"fixture-v2\""
                test.runOnMainSync { store.resume(changed) }; await("changed rejected") { task(changed)?.state==Download.STATE_FAILED }
                check(server.conditional.get()>0)
                server.slow=false
                test.runOnMainSync { store.restart(changed) }; await("restart command") { task(changed)?.state!=Download.STATE_FAILED }; complete(changed)
                check(read(changed,"/video.mp4").contentEquals(files.getValue("/video.mp4")))
                val hls=enqueue("hls","/offline-hls/index.m3u8","application/x-mpegURL"); complete(hls)
                val dash=enqueue("dash","/offline-dash/index.mpd","application/dash+xml"); complete(dash)
                server.close()
                for((id,folder) in listOf(hls to "offline-hls",dash to "offline-dash")) {
                    files.filterKeys { it.startsWith("/$folder/") }.forEach { (path,expected) -> check(read(id,path).contentEquals(expected)) { "cache mismatch $path" } }
                    val rendered=AtomicBoolean()
                    test.runOnMainSync {
                        engine=NativePlayer(context,task(id)!!.request.uri.toString(),offlineId=id)
                        engine!!.player.volume=0f
                        engine!!.player.addListener(object:androidx.media3.common.Player.Listener { override fun onRenderedFirstFrame() { rendered.set(true) } })
                        activity.setContent { PlaybackVideoSurface(engine!!.player,true) }
                        engine!!.open(PlaybackRequest(task(id)!!.request.uri.toString(),emptyMap(),folder,mimeType=task(id)!!.request.mimeType,offlineId=id))
                    }
                    await("offline frame $folder") { rendered.get() }
                    Thread.sleep(1200)
                    test.uiAutomation.takeScreenshot()?.let { bitmap -> java.io.File(context.getExternalFilesDir(null),"$folder-player.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle() }
                    test.runOnMainSync { activity.setContent { }; engine!!.release(); engine=null }; test.waitForIdleSync()
                }
            }
            check(context.getSharedPreferences("tv_settings",0).all==settings)
            check(context.getSharedPreferences("tv_library",0).all==library)
        } catch(error:Throwable) { android.util.Log.e("AdaptiveDownloadTest",mode,error); throw error }
        finally {
            server.close()
            for(id in owned) { test.runOnMainSync { store.remove(id) }; await("cleanup") { task(id)==null }; check(store.cache.keys.none { it.startsWith("$id|") }) }
            test.runOnMainSync { activity.setContent { }; engine?.release(); activity.finish() }
        }
    }
    internal class Server(private val files:Map<String,ByteArray>,requestedPort:Int) {
        private val server=ServerSocket().apply { reuseAddress=true; bind(java.net.InetSocketAddress("127.0.0.1",requestedPort)) }
        val port=server.localPort
        val redirects=java.util.concurrent.ConcurrentHashMap<String,String>()
        val requests=java.util.concurrent.ConcurrentLinkedQueue<String>()
        val stalled=AtomicInteger(); val stalledClosed=AtomicInteger()
        @Volatile var stallPath:String?=null
        val freshHeaders=java.util.concurrent.ConcurrentLinkedQueue<String>()
        val transferred=AtomicInteger(); val ranges=AtomicInteger(); val conditional=AtomicInteger()
        @Volatile var tag="\"fixture-v1\""
        @Volatile var slow=false
        @Volatile private var closed=false
        init { Thread { while(!closed) { val socket=try { server.accept() } catch(_:Exception) { break }; Thread { serve(socket) }.apply { isDaemon=true; start() } } }.apply { isDaemon=true; start() } }
        private fun serve(socket:Socket) {
            try { socket.use {
                val reader=it.getInputStream().bufferedReader(); val path=reader.readLine()?.split(' ')?.getOrNull(1) ?: return
                val headers=mutableMapOf<String,String>(); while(true) { val line=reader.readLine() ?: break; if(line.isEmpty())break; headers[line.substringBefore(':').lowercase()]=line.substringAfter(':').trim() }
                requests.add(path)
                if(path==stallPath) {
                    stalled.incrementAndGet(); socket.soTimeout=500
                    while(!closed) {
                        try { if(reader.read()<0) { stalledClosed.incrementAndGet(); return } }
                        catch(_:java.net.SocketTimeoutException) { }
                    }
                    return
                }
                headers["x-fresh"]?.let { freshHeaders.add(it) }
                redirects[path]?.let { target -> it.getOutputStream().write(("HTTP/1.1 302 Found\r\nLocation: $target\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").toByteArray()); return }
                val payload=files[path] ?: return
                if(headers["if-match"]!=null)conditional.incrementAndGet()
                val start=headers["range"]?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                if(start>0)ranges.incrementAndGet()
                val output=it.getOutputStream()
                val code=if(start>0)"206 Partial Content\r\nContent-Range: bytes $start-${payload.lastIndex}/${payload.size}\r\n" else "200 OK\r\n"
                val mime=when { path.endsWith("m3u8")->"application/x-mpegURL"; path.endsWith("mpd")->"application/dash+xml"; path.endsWith("ts")->"video/mp2t"; else->"video/mp4" }
                // Intentionally ignores If-Match: the client must also verify the response validator.
                output.write(("HTTP/1.1 $code"+"ETag: $tag\r\nContent-Type: $mime\r\nContent-Length: ${payload.size-start}\r\nConnection: close\r\n\r\n").toByteArray())
                var position=start
                while(position<payload.size && !closed) { val count=minOf(if(slow)1024 else 16384,payload.size-position); output.write(payload,position,count); output.flush(); transferred.addAndGet(count); position+=count; if(slow)Thread.sleep(20) }
            } } catch(_:Exception) { }
        }
        fun close() { closed=true; server.close() }
    }
}
