package org.kazumi.tv

import android.content.Context
import kotlinx.coroutines.*
import org.json.JSONObject
import org.kazumi.tv.playback.*
import org.kazumi.tv.rules.*
import java.net.ServerSocket
import java.net.InetAddress
import kotlin.concurrent.thread

object S2ResolverRegression {
    fun run(context: Context) = runBlocking {
        val server=ServerSocket(0,16,InetAddress.getByName("127.0.0.1"))
        val base="http://127.0.0.1:${server.localPort}"
        val acceptor=thread(isDaemon=true) {
            while(!server.isClosed) try {
                val socket=server.accept()
                thread(isDaemon=true) { socket.use {
                    it.soTimeout=3000
                    val reader=it.getInputStream().bufferedReader()
                    val path=reader.readLine()?.split(' ')?.getOrNull(1).orEmpty()
                    val headers = mutableListOf<String>()
                    while(true) { val line=reader.readLine(); if(line.isNullOrEmpty()) break; headers.add(line) }
                    val body=when(path) {
                        "/late" -> "<html><script>setTimeout(function(){var v=document.createElement('video');v.src='/stream';document.body.appendChild(v);},700);</script><body>late</body></html>"
                        "/xhr" -> "<html><script>setTimeout(function(){var x=new XMLHttpRequest();x.open('GET','/manifest');x.send();},700);</script></html>"
                        "/fetch" -> "<html><script>setTimeout(function(){fetch('/typed');},700);</script></html>"
                        "/textfetch" -> "<html><script>setTimeout(function(){fetch('/manifest').then(function(r){return r.text();});},700);</script></html>"
                        "/parent" -> "<html><iframe src='/child'></iframe></html>"
                        "/child" -> "<html><script>setTimeout(function(){var v=document.createElement('video');v.src='/guard';document.body.appendChild(v);},700);</script><body></body></html>"
                        "/legacy" -> "<html><iframe src='/parser?url="+java.net.URLEncoder.encode("$base/legacy.m3u8?token=a%2Bb&part=2","UTF-8")+"'></iframe></html>"
                        "/guard", "/legacy.m3u8?token=a%2Bb&part=2" -> "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXTINF:4,\nsegment.ts\n#EXT-X-ENDLIST\n"
                        "/stream", "/manifest", "/typed" -> "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXTINF:4,\nsegment.ts\n#EXT-X-ENDLIST\n"
                        "/fake" -> "<html><video src='/fake.mp4'></video></html>"
                        "/fake.mp4" -> "<!doctype html><html>not video</html>"
                        "/challenge" -> "<html><title>系统安全验证</title></html>"
                        else -> "<html><body>empty</body></html>"
                    }.toByteArray()
                    val status=if(path=="/status") "503 Service Unavailable" else if(path=="/guard" && headers.none { it.equals("Referer: $base/child",true) }) "403 Forbidden" else "200 OK"
                    val type=if(path=="/typed") "application/x-mpegURL" else if(path in listOf("/stream","/manifest")) "text/plain" else "text/html"
                    try { it.getOutputStream().write("HTTP/1.1 $status\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()+body) } catch(_:Exception) {}
                } }
            } catch(_:Exception) { if(server.isClosed) break }
        }
        val rule=SourceRule(JSONObject().put("name","fixture").put("baseURL",base))
        try {
            for (path in listOf("/late","/xhr","/fetch","/textfetch","/parent")) {
                val request=WebMediaResolver(context,6000).resolve(base+path,rule,"fixture")
                check(request.mimeType=="application/x-mpegURL") { "$path did not discover manifest" }
                check(request.url==base+when(path){"/late"->"/stream";"/fetch"->"/typed";"/parent"->"/guard";else->"/manifest"})
                if(path=="/parent") check(request.headers["Referer"]=="$base/child")
            }
            val legacyRule=SourceRule(JSONObject(rule.json.toString()).put("useLegacyParser",true))
            val legacy=WebMediaResolver(context,6000).resolve("$base/legacy",legacyRule,"fixture")
            check(legacy.url=="$base/legacy.m3u8?token=a%2Bb&part=2")
            try { WebMediaResolver(context,2500).resolve("$base/fake",rule,"fixture"); error("HTML accepted") }
            catch(e:MediaResolutionFailure) { check(e.stage=="媒体探测") }
            try { WebMediaResolver(context,3000).resolve("$base/challenge",rule,"fixture"); error("challenge accepted") }
            catch(e:SourceVerificationRequired) { check(e.pageUrl=="$base/challenge") }
            try { WebMediaResolver(context,3000).resolve("$base/status",rule,"fixture"); error("HTTP503 accepted") }
            catch(e:MediaResolutionFailure) { check(e.message.orEmpty().contains("503")) }
            try { withTimeout(400) { WebMediaResolver(context).resolve("$base/empty",rule,"fixture") }; error("cancel lost") }
            catch(_:TimeoutCancellationException) {}
        } finally { server.close(); acceptor.join(1000) }
    }
}
