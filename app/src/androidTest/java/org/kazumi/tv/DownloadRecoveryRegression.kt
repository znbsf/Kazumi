@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package org.kazumi.tv
import android.app.Instrumentation
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import org.kazumi.tv.data.*
import org.kazumi.tv.download.*
import org.kazumi.tv.playback.*
import org.kazumi.tv.ui.*
import androidx.media3.exoplayer.offline.Download
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object DownloadRecoveryRegression {
    fun run(test:Instrumentation,mode:String) {
        val context=test.targetContext
        val settings=context.getSharedPreferences("tv_settings",0).all.toMap()
        val library=context.getSharedPreferences("tv_library",0).all.toMap()
        val phase=context.getSharedPreferences("download_recovery_test",0)
        val journal=context.getSharedPreferences("download_replacement",0)
        val reopening=mode=="download-recovery-reopen"
        check(reopening || !journal.contains("pending")) { "another replacement is pending" }
        val payload=test.context.assets.open("tracks-fixture.mp4").use { it.readBytes() }
        val server=AdaptiveDownloadRegression.Server(mapOf("/old.mp4" to payload,"/new.mp4" to payload),if(reopening)phase.getInt("port",0) else 0)
        val activity=test.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        var store:OfflineDownloads?=null
        test.runOnMainSync { store=OfflineDownloads.get(context) }
        val repository=store!!
        val key="fixture|http://127.0.0.1:${server.port}/episode"
        val id=if(reopening)phase.getString("id",null)!! else DownloadMetadata.id(19000918,key)
        fun task()=repository.all().firstOrNull { it.request.id==id }
        fun await(label:String,condition:()->Boolean) { val end=System.currentTimeMillis()+45000; while(System.currentTimeMillis()<end) { if(condition())return; Thread.sleep(75) }; error("timeout $label") }
        fun nodes():List<AccessibilityNodeInfo> {
            val all=mutableListOf<AccessibilityNodeInfo>()
            fun walk(node:AccessibilityNodeInfo?) { if(node==null)return; all+=node; for(i in 0 until node.childCount)walk(node.getChild(i)) }
            walk(test.uiAutomation.rootInActiveWindow); return all
        }
        fun click(text:String) {
            await("button $text") { nodes().any { it.text?.toString()==text } }
            var node=nodes().first { it.text?.toString()==text }
            while(!node.isClickable && node.parent!=null)node=node.parent
            check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)); test.waitForIdleSync()
        }
        var retained=false
        var shown by mutableStateOf(false)
        val closed=AtomicBoolean()
        fun show(expected:Download,resolver:(suspend ()->PlaybackRequest)?=null) {
            closed.set(false)
            test.runOnMainSync { shown=true; activity.setContent { KazumiTheme(false) { CompositionLocalProvider(androidx.tv.material3.LocalContentColor provides Color(0xffe4eee1)) {
                if(shown)DownloadRecoveryPanel(expected,resolveOverride=resolver,onClose={shown=false;closed.set(true)})
            } } } }
            test.waitForIdleSync()
        }
        val resolved=PlaybackRequest("http://127.0.0.1:${server.port}/new.mp4",mapOf("X-Fresh" to "new-token"),"updated title",mimeType="video/mp4")
        try {
            if(reopening) {
                check(android.os.Process.myPid()!=phase.getInt("pid",0))
                test.runOnMainSync { androidx.media3.exoplayer.offline.DownloadService.start(context,TvDownloadService::class.java) }
            } else {
                check(task()==null)
                server.slow=true
                test.runOnMainSync { repository.enqueue(PlaybackRequest("http://127.0.0.1:${server.port}/old.mp4",emptyMap(),"恢复下载 · 第1集",key,"video/mp4"),Subject(19000918,"恢复下载","",""),PlaybackOrigin("fixture","来源","http://127.0.0.1:${server.port}/source","线路")) }
                await("partial bytes") { server.transferred.get()>20000 }
                test.runOnMainSync { repository.pause(id) }; await("paused") { task()?.state==Download.STATE_STOPPED }
                val expected=task()!!
                if(mode=="download-recovery-stage") {
                    val old=DownloadMetadata.read(expected.request.data)
                    val media=ReplacementMedia(id,resolved.url,resolved.mimeType,DownloadMetadata(old.subject,old.title,old.resumeKey,resolved.headers,old.origin).bytes().toString(Charsets.UTF_8))
                    check(journal.edit().putString("pending",DownloadReplacement(media).json()).commit())
                    check(phase.edit().putInt("pid",android.os.Process.myPid()).putInt("port",server.port).putString("id",id).commit())
                    retained=true
                } else {
                    show(expected)
                    await("disabled original rule") { nodes().any { it.text?.toString()?.contains("原来源未启用")==true } }
                    click("返回下载列表"); await("close missing rule") { closed.get() }
                    val late=CompletableDeferred<PlaybackRequest>()
                    show(expected) { late.await() }
                    click("返回下载列表"); await("cancel") { closed.get() }; late.complete(resolved); Thread.sleep(200)
                    check(task()!!.request==expected.request && !journal.contains("pending"))
                    val attempts=AtomicInteger()
                    show(expected) { if(attempts.incrementAndGet()==1)error("fixture failure"); resolved }
                    click("重新解析"); await("confirmation") { nodes().any { it.text?.toString()=="用新地址重新下载" } }
                    test.uiAutomation.takeScreenshot()?.let { bitmap -> java.io.File(context.getExternalFilesDir(null),"download-recovery.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle() }
                    server.slow=false
                    click("用新地址重新下载"); await("closed after apply") { closed.get() }
                }
            }
            if(!retained) {
                await("new complete") { task()?.state==Download.STATE_COMPLETED && task()?.request?.uri?.toString()==resolved.url }
                await("journal settled") { !journal.contains("pending") }
                val current=task()!!; val metadata=DownloadMetadata.read(current.request.data)
                check(metadata.title=="恢复下载 · 第1集" && metadata.headers==resolved.headers && metadata.origin?.rule=="fixture")
                check(server.freshHeaders.contains("new-token"))
                check(repository.cache.keys.none { it.startsWith("$id|") && it.endsWith("/old.mp4") })
                server.close()
                val source=repository.offlineFactory(id).createDataSource(); val output=java.io.ByteArrayOutputStream()
                try { source.open(androidx.media3.datasource.DataSpec(current.request.uri)); val buffer=ByteArray(8192); while(true) { val n=source.read(buffer,0,buffer.size); if(n<0)break; output.write(buffer,0,n) } } finally { source.close() }
                check(output.toByteArray().contentEquals(payload))
                var rejected=false
                test.runOnMainSync { rejected=runCatching { repository.replaceResolved(current,resolved) }.isFailure }
                check(rejected) // A completed task must not be overwritten by a late confirmation.
                test.runOnMainSync { repository.remove(id) }; await("delete") { task()==null }
                test.runOnMainSync { rejected=runCatching { repository.replaceResolved(current,resolved) }.isFailure }
                check(rejected && task()==null && !journal.contains("pending"))
                phase.edit().clear().commit()
            }
            check(context.getSharedPreferences("tv_settings",0).all==settings)
            check(context.getSharedPreferences("tv_library",0).all==library)
        } catch(error:Throwable) { android.util.Log.e("DownloadRecoveryTest",mode,error); throw error }
        finally {
            server.close()
            if(!retained) { test.runOnMainSync { repository.remove(id) }; await("cleanup") { task()==null } }
            test.runOnMainSync { shown=false; activity.setContent { }; activity.finish() }
        }
    }
}
