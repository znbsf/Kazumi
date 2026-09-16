package org.kazumi.tv

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.*
import org.kazumi.tv.data.*
import org.kazumi.tv.ui.*
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

object S4CacheRegression {
    fun run(test: Instrumentation) = runBlocking {
        val slowStarted=CountDownLatch(1)
        val slowClosed=CountDownLatch(1)
        val covers=AtomicInteger()
        val image=android.graphics.Bitmap.createBitmap(32,48,android.graphics.Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.rgb(80,160,100))
        val bytes=java.io.ByteArrayOutputStream().also { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }.toByteArray()
        image.recycle()
        val server=ServerSocket(0,16,InetAddress.getByName("127.0.0.1"))
        val base="http://127.0.0.1:${server.localPort}"
        val acceptor=thread(isDaemon=true) {
            while(!server.isClosed)try {
                val socket=server.accept()
                thread(isDaemon=true) { socket.use {
                    try {
                        socket.soTimeout=4000
                        val reader=socket.getInputStream().bufferedReader()
                        val path=reader.readLine()?.split(' ')?.getOrNull(1).orEmpty()
                        while(true) { if(reader.readLine().isNullOrEmpty())break }
                        val out=socket.getOutputStream()
                        if(path=="/slow") {
                            out.write("HTTP/1.1 200 OK\r\nContent-Length: 999999\r\n\r\nx".toByteArray()); out.flush(); slowStarted.countDown()
                            if(reader.read()==-1)slowClosed.countDown()
                        } else {
                            val failed=path=="/cover" && covers.incrementAndGet()==1
                            val body=if(path=="/cover")bytes else "x".repeat(100).toByteArray()
                            val type=if(path=="/cover")"image/png" else "text/plain"
                            out.write("HTTP/1.1 ${if(failed) "503 Service Unavailable" else "200 OK"}\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()+body)
                        }
                    } catch(_:Exception) {}
                } }
            } catch(_:Exception) { if(server.isClosed)break }
        }
        val originalMirror=NetworkSettings.catalogMirror
        var activity: MainActivity?=null
        try {
            try { HttpText.requestAsync("$base/large",maxChars=16); error("oversize accepted") }
            catch(e:IllegalStateException) { check(e.message=="响应超过大小限制") }
            val pending=launch(Dispatchers.IO) { HttpText.requestAsync("$base/slow") }
            check(withContext(Dispatchers.IO) { slowStarted.await(3,TimeUnit.SECONDS) })
            pending.cancelAndJoin()
            check(withContext(Dispatchers.IO) { slowClosed.await(3,TimeUnit.SECONDS) }) { "HTTP socket stayed open after cancellation" }

            val calls=AtomicInteger()
            val library=test.targetContext.getSharedPreferences("tv_library",Context.MODE_PRIVATE).all.toMap()
            lateinit var model: SearchViewModel
            test.runOnMainSync {
                model=SearchViewModel(SavedStateHandle(mapOf("query" to "cache-test"))) { _,_,_ ->
                    calls.incrementAndGet(); listOf(Subject(1,"fixture","",""))
                }
            }
            withTimeout(3000) { while(calls.get()<1)delay(50) }
            test.runOnMainSync { NetworkSettings.invalidateCatalog() }
            withTimeout(3000) { while(calls.get()<2)delay(50) }
            test.runOnMainSync { NetworkSettings.catalogMirror=!originalMirror }
            withTimeout(3000) { while(calls.get()<3)delay(50) }
            check(library==test.targetContext.getSharedPreferences("tv_library",Context.MODE_PRIVATE).all)
            test.runOnMainSync { NetworkSettings.catalogMirror=originalMirror }

            activity=test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            test.runOnMainSync { activity.setContent { KazumiTheme(false) {
                CoverImage("$base/cover","测试封面",Modifier.width(158.dp).height(237.dp),allowRetry=true)
            } } }
            fun nodes(): List<AccessibilityNodeInfo> {
                val result=mutableListOf<AccessibilityNodeInfo>()
                fun visit(node: AccessibilityNodeInfo?) { if(node==null)return; result.add(node); for(i in 0 until node.childCount)visit(node.getChild(i)) }
                visit(test.uiAutomation.rootInActiveWindow); return result
            }
            var action: AccessibilityNodeInfo?=null
            withTimeout(5000) { while(action==null) { action=nodes().firstOrNull { it.text?.toString()=="重试封面" }; delay(100) } }
            while(action!=null && !action!!.isClickable)action=action!!.parent
            check(action?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true)
            withTimeout(5000) { while(covers.get()<2 || nodes().any { it.text?.toString() in listOf("封面加载失败","封面加载中…") })delay(100) }
        } finally {
            test.runOnMainSync { NetworkSettings.catalogMirror=originalMirror; activity?.finish() }
            server.close(); acceptor.join(1000)
        }
    }
}
