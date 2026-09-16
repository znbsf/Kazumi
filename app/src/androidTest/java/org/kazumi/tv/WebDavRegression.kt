package org.kazumi.tv
import android.app.Instrumentation
import android.content.ContextWrapper
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.kazumi.tv.data.*
import org.kazumi.tv.ui.*
import java.net.ServerSocket
import java.util.concurrent.atomic.*

object WebDavRegression {
    fun run(test:Instrumentation) {
        val actual=test.targetContext.getSharedPreferences("tv_library",0).all.toMap()
        val actualCredentials=test.targetContext.getSharedPreferences("webdav_credentials",0).all.toMap()
        val context=object:ContextWrapper(test.targetContext) { override fun getSharedPreferences(name:String,mode:Int)=baseContext.getSharedPreferences("webdav_test_$name",mode) }
        val prefs=context.getSharedPreferences("tv_library",0); prefs.edit().clear().commit()
        val credentialPrefs=context.getSharedPreferences("webdav_credentials",0); credentialPrefs.edit().clear().commit()
        val library=LibraryStore(context)
        val a=Subject(951,"同步本地作品","",""); val b=Subject(952,"同步远端作品","",""); val c=Subject(953,"提交期间新增","","")
        val server=ServerSocket(0,8,java.net.InetAddress.getByName("127.0.0.1"))
        val raw=AtomicReference<String?>(null); val revision=AtomicInteger(1)
        val fault=AtomicReference(""); val requests=AtomicInteger(); val writes=AtomicInteger(); val closed=AtomicInteger()
        val failure=AtomicReference<Throwable?>(); val running=AtomicBoolean(true)
        val worker=Thread {
            try { while(running.get())server.accept().use { socket ->
                socket.soTimeout=8000
                val input=socket.getInputStream()
                fun line():String { val bytes=java.io.ByteArrayOutputStream(); while(true) { val byte=input.read(); check(byte>=0); if(byte==10)break; if(byte!=13)bytes.write(byte) }; return bytes.toString("UTF-8") }
                val first=line(); val headers=mutableMapOf<String,String>()
                while(true) { val l=line(); if(l.isEmpty())break; headers[l.substringBefore(':').lowercase()]=l.substringAfter(':').trim() }
                check(headers["authorization"]==okhttp3.Credentials.basic("fixture-user","fixture-password",Charsets.UTF_8))
                val length=headers["content-length"]?.toInt() ?: 0; val body=ByteArray(length); var read=0
                while(read<length) { val n=input.read(body,read,length-read); check(n>0); read+=n }
                requests.incrementAndGet()
                val mode=fault.getAndSet("")
                android.util.Log.i("WebDavTest","request=${requests.get()} method=${first.substringBefore(' ')} fault=$mode")
                if(mode=="hang") { check(input.read()==-1); closed.incrementAndGet(); return@use }
                var code=200; var payload=""; var extra=""
                when {
                    mode=="auth" -> code=401
                    mode=="redirect" -> { code=302; extra="Location: http://127.0.0.1:${server.localPort}/redirected\r\n" }
                    first.startsWith("GET ") -> {
                        if(raw.get()==null)code=404 else {
                            payload=if(mode=="bad")"not-json" else raw.get()!!
                            extra="ETag: ${if(mode=="weak")"W/" else ""}\"${revision.get()}\"\r\n"
                        }
                    }
                    first.startsWith("PUT ") -> {
                        if(mode=="conflict")revision.incrementAndGet()
                        val condition=if(raw.get()==null)headers["if-none-match"]=="*" else headers["if-match"]=="\"${revision.get()}\""
                        if(!condition)code=412 else {
                            val next=String(body,Charsets.UTF_8); CollectionSnapshotCodec.read(next)
                            raw.set(next); revision.incrementAndGet(); writes.incrementAndGet(); code=204
                            if(mode=="local-change")library.setCollection(c,CollectionType.PLANNED)
                            if(mode=="ack-lost")return@use
                        }
                    }
                    else -> error("Unexpected method")
                }
                android.util.Log.i("WebDavTest","response=$code")
                val bytes=payload.toByteArray(Charsets.UTF_8)
                socket.getOutputStream().write(("HTTP/1.1 $code Test\r\n${extra}Content-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray()+bytes)
                socket.getOutputStream().flush()
            } } catch(e:Throwable) { if(running.get()) { failure.set(e); android.util.Log.e("WebDavTest","server failure: ${e.javaClass.simpleName}") } }
        }.apply { isDaemon=true; start() }
        val account=WebDavAccount("http://127.0.0.1:${server.localPort}/dav","fixture-user","fixture-password")
        fun service()=WebDavCollections(account,allowLoopbackForTest=true)
        var activity:MainActivity?=null
        try {
            runBlocking {
                library.setCollection(a,CollectionType.PLANNED)
                val first=service().preview(library); check(first.remote.missing)
                service().commit(library,first); check(writes.get()==1)
                val unchanged=service().preview(library); check(unchanged.uploads==0)
                service().commit(library,unchanged); check(library.collections().single().subject.id==951)
                val remoteEntry=CollectionEntry(b,CollectionType.WATCHING,100)
                val previous=CollectionSnapshotCodec.read(raw.get()!!)
                val remoteEvents=CollectionChanges.append(previous.changes,previous.collections,previous.collections+remoteEntry,101)
                raw.set(CollectionSnapshotCodec.write(CollectionSnapshot(previous.collections+remoteEntry,remoteEvents))); revision.incrementAndGet()
                library.setCollection(a,null)
                val preview=service().preview(library); check(preview.merged.collections.single().subject.id==952)
                val old=library.collectionSyncSnapshot(); fault.set("conflict")
                check(runCatching { service().commit(library,preview) }.isFailure); check(library.collectionSyncSnapshot()==old)
                service().commit(library,service().preview(library)); check(library.collections().single().subject.id==952)
                for(mode in listOf("auth","weak","bad","redirect")) {
                    val count=requests.get(); val current=library.collectionSyncSnapshot(); fault.set(mode)
                    check(runCatching { service().preview(library) }.isFailure)
                    check(requests.get()==count+1 && library.collectionSyncSnapshot()==current)
                }
                val next=service().preview(library); library.setCollection(a,CollectionType.WATCHED)
                val beforeWrites=writes.get(); check(runCatching { service().commit(library,next) }.isFailure); check(writes.get()==beforeWrites)
                val during=service().preview(library); fault.set("local-change")
                check(runCatching { service().commit(library,during) }.isFailure); check(library.collections().any { it.subject.id==953 })
                service().commit(library,service().preview(library)); check(library.collections().size==3)
                library.setCollection(a,CollectionType.ABANDONED)
                val uncertain=service().preview(library); val stateBeforeLoss=library.collectionSyncSnapshot()
                fault.set("ack-lost"); check(runCatching { service().commit(library,uncertain) }.isFailure)
                check(library.collectionSyncSnapshot()==stateBeforeLoss)
                val retry=service().preview(library); check(retry.uploads==0)
                service().commit(library,retry); check(library.collections().first { it.subject.id==951 }.type==CollectionType.ABANDONED)
                val unauthorized=service().preview(library); val beforeAuth=library.collectionSyncSnapshot()
                fault.set("auth"); check(runCatching { service().commit(library,unauthorized) }.isFailure)
                check(library.collectionSyncSnapshot()==beforeAuth)
                val count=requests.get(); fault.set("hang")
                val task=launch { service().read() }
                withTimeout(5000) { while(requests.get()==count)delay(20) }
                task.cancelAndJoin(); withTimeout(5000) { while(closed.get()==0)delay(20) }
            }
            val credentialStore=WebDavCredentialStore(context)
            credentialStore.save(WebDavAccount("https://fixture.invalid/dav","fixture-user","fixture-password"))
            check(WebDavCredentialStore(context).read()?.password=="fixture-password")
            check(!credentialPrefs.getString("value","")!!.contains("fixture-password"))
            val screen=test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity; activity=screen
            fun nodes():List<AccessibilityNodeInfo> {
                val result=mutableListOf<AccessibilityNodeInfo>(); fun walk(n:AccessibilityNodeInfo?) { if(n==null)return; result.add(n); for(i in 0 until n.childCount)walk(n.getChild(i)) }; walk(test.uiAutomation.rootInActiveWindow); return result
            }
            fun find(text:String):AccessibilityNodeInfo {
                repeat(75) { nodes().firstOrNull { it.text?.toString()==text }?.let { return it }; nodes().firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD); Thread.sleep(100) }; error("Missing $text")
            }
            fun click(text:String) { var n:AccessibilityNodeInfo?=find(text); while(n!=null && !n.isClickable)n=n.parent; check(n?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true); Thread.sleep(300) }
            test.runOnMainSync { screen.setContent { CompositionLocalProvider(LocalContext provides context) { KazumiTheme(false) {
                Box(Modifier.fillMaxSize().background(KazumiColors.background).padding(30.dp)) { WebDavSettings { service() } }
            } } } }
            click("保存WebDAV配置"); find("配置已加密保存")
            fault.set("auth"); click("连接并预览同步"); find("WebDAV认证失败或目录无权限")
            click("连接并预览同步"); find("同步预览"); click("取消预览")
            click("连接并预览同步"); find("同步预览")
            val shot=test.uiAutomation.takeScreenshot(); java.io.File(test.targetContext.getExternalFilesDir(null),"webdav-preview.png").outputStream().use { shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; shot.recycle()
            click("确认同步收藏"); find("收藏同步完成")
            val remoteBeforeRemove=raw.get(); click("移除本机WebDAV配置"); find("已移除本机配置，远端文件保留")
            check(credentialStore.read()==null && raw.get()==remoteBeforeRemove)
            check(actual==test.targetContext.getSharedPreferences("tv_library",0).all)
            check(actualCredentials==test.targetContext.getSharedPreferences("webdav_credentials",0).all)
            failure.get()?.let { throw it }
        } catch(e:Exception) { android.util.Log.e("WebDavTest","test failure: ${e.javaClass.simpleName}: ${if(e is WebDavFailure)e.message else "assertion"}"); android.util.Log.e("WebDavTest","cause=${e.cause?.javaClass?.simpleName} frames=${e.cause?.stackTrace?.take(7)?.joinToString()}"); throw e } finally {
            activity?.let { screen -> test.runOnMainSync { screen.finish() } }
            running.set(false); server.close(); worker.join(2000)
            prefs.edit().clear().commit(); credentialPrefs.edit().clear().commit()
        }
    }
}
