package org.kazumi.tv
import android.app.Instrumentation
import android.content.ContextWrapper
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import org.kazumi.tv.data.*
import org.kazumi.tv.rules.*
import org.kazumi.tv.playback.PlaybackRequest
import org.kazumi.tv.ui.*
import java.util.concurrent.atomic.*

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object SourceTransferRegression {
    fun run(test:Instrumentation) {
        val actual=test.targetContext.getSharedPreferences("tv_library",0).all.toMap()
        val context=object:ContextWrapper(test.targetContext) { override fun getSharedPreferences(name:String,mode:Int)=baseContext.getSharedPreferences("transfer_test_$name",mode) }
        val prefs=context.getSharedPreferences("tv_library",0); prefs.edit().clear().commit()
        val settings=TvPreferences(context); settings.danmakuEnabled=false; settings.incognito=false; settings.controlsSeconds=10
        val library=LibraryStore(context)
        val sample=java.io.File(test.targetContext.cacheDir,"source-transfer-fixture.mp4")
        test.context.assets.open("tracks-fixture.mp4").use { input -> sample.outputStream().use { input.copyTo(it) } }
        val subject=Subject(971,"来源迁移样片","","")
        val current=Episode("第10集","https://a.invalid/10")
        val initialRoads=listOf(Road("原线路",listOf(current)))
        val failed=AtomicBoolean(false); val resolved=AtomicReference("")
        val catalog=object:SourceCatalog {
            override val rules=listOf("来源A","来源B","来源C").map { SourceRule(org.json.JSONObject().put("name",it).put("baseURL","https://fixture.invalid")) }
            override suspend fun search(rule:SourceRule,keyword:String)=listOf(SourceMatch("来源迁移样片 第一期","https://${rule.name.last().lowercase()}.invalid/show"))
            override suspend fun chapters(rule:SourceRule,match:SourceMatch):List<Road> {
                if(rule.name=="来源B" && !failed.getAndSet(true))error("测试线路暂不可用")
                return when(rule.name) {
                    "来源A" -> initialRoads
                    "来源B" -> listOf(Road("对应线路",(9..11).map { Episode("第${it}集","https://b.invalid/$it") }))
                    else -> listOf(Road("重号线路",listOf(Episode("第10集",current.pageUrl),Episode("第10集","https://c.invalid/10b"))))
                }
            }
        }
        library.save(HistoryEntry("来源C|https://c.invalid/10b",subject,"第10集",12000,16000,PlaybackOrigin("来源C","old","https://c.invalid/show","重号线路")))
        val activity=test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            fun nodes():List<AccessibilityNodeInfo> {
                val result=mutableListOf<AccessibilityNodeInfo>(); fun walk(n:AccessibilityNodeInfo?) { if(n==null)return; result.add(n); for(i in 0 until n.childCount)walk(n.getChild(i)) }; walk(test.uiAutomation.rootInActiveWindow); return result
            }
            fun find(text:String):AccessibilityNodeInfo {
                repeat(70) { nodes().firstOrNull { it.text?.toString()==text }?.let { return it }; Thread.sleep(100) }; error("Missing $text")
            }
            fun click(text:String) { var n:AccessibilityNodeInfo?=find(text); while(n!=null && !n.isClickable)n=n.parent; check(n?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true); Thread.sleep(300) }
            fun player():Player? {
                fun find(v:View):Player? {
                    if(v is PlayerView && v.player!=null)return v.player
                    if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let { return it }
                    return null
                }; return find(activity.window.decorView)
            }
            fun awaitPlayer(rule:String):Long {
                repeat(100) {
                    var ready=false; var position=0L
                    test.runOnMainSync { val p=player(); ready=resolved.get()==rule && p?.playbackState==Player.STATE_READY && p.videoSize.width>0; position=p?.currentPosition ?: 0 }
                    if(ready)return position
                    Thread.sleep(100)
                }; error("Player not ready for $rule")
            }
            fun pauseAt(position:Long?=null) {
                test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
                test.runOnMainSync { player()!!.apply { pause(); position?.let { seekTo(it) } } }; Thread.sleep(200)
            }
            test.runOnMainSync { activity.setContent { CompositionLocalProvider(LocalContext provides context) { KazumiTheme(false) {
                PlaybackSessionScreen(subject,"来源A",current,initialRoads,initialOrigin=PlaybackOrigin("来源A","来源迁移样片 第一期","https://a.invalid/show","原线路"),sourceCatalog=catalog,
                    resolveEpisode={ rule,ep -> resolved.set(rule); PlaybackRequest(sample.toURI().toString(),emptyMap(),"${subject.title} · ${ep.title}") },onClose={})
            } } } }
            awaitPlayer("来源A"); pauseAt(6500); click("换源"); find("更换来源 · 当前 第10集")
            click("来源B"); click("来源迁移样片 第一期"); find("测试线路暂不可用"); click("重试")
            find("续播 第10集 · 保留进度")
            val shot=test.uiAutomation.takeScreenshot(); java.io.File(test.targetContext.getExternalFilesDir(null),"source-transfer-matched.png").outputStream().use { shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; shot.recycle()
            click("续播 第10集 · 保留进度"); val transferred=awaitPlayer("来源B"); check(transferred in 6250..9500) { "Matched progress $transferred" }
            pauseAt(); click("换源"); click("返回播放")
            val restored=awaitPlayer("来源B"); check(restored>=6250)
            test.runOnMainSync { check(player()!!.playWhenReady==false) { "Cancelled source choice lost pause intent" } }
            pauseAt(); click("换源"); click("来源C"); click("来源迁移样片 第一期")
            find("未找到唯一同集，请手动选集；所选集从头播放。")
            check(nodes().none { it.text?.toString()=="续播 第10集 · 保留进度" })
            click("定位序号"); find("定位选集")
            test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_UP); Thread.sleep(150)
            val input=nodes().first { it.isEditable }; check(input.isFocused)
            check(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"2") }))
            click("跳转"); Thread.sleep(500)
            val ambiguous=test.uiAutomation.takeScreenshot(); java.io.File(test.targetContext.getExternalFilesDir(null),"source-transfer-ambiguous.png").outputStream().use { ambiguous.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; ambiguous.recycle()
            click("2. 已看 · 第10集"); val reset=awaitPlayer("来源C"); check(reset<2500) { "Manual selection resumed $reset" }
            Thread.sleep(500); pauseAt()
            val video=test.uiAutomation.takeScreenshot(); java.io.File(test.targetContext.getExternalFilesDir(null),"source-transfer-video.png").outputStream().use { video.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; video.recycle()
            test.runOnMainSync { activity.setContent { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier) } }
            var disposed=false; val deadline=System.nanoTime()+5_000_000_000L
            while(!disposed && System.nanoTime()<deadline) { test.runOnMainSync { disposed=player()==null }; if(!disposed)Thread.sleep(50) }
            check(disposed) { "Player disposal not observed" }
            val history=library.history()
            check(history.first { it.key=="来源A|https://a.invalid/10" }.position>=6250)
            check(history.first { it.key=="来源B|https://b.invalid/10" }.let { it.position>=6250 && it.origin?.rule=="来源B" && it.origin?.sourceUrl=="https://b.invalid/show" })
            check(history.first { it.key=="来源C|https://c.invalid/10b" }.let { it.position in 1..2499 && it.origin?.rule=="来源C" })
            check(actual==test.targetContext.getSharedPreferences("tv_library",0).all)
        } catch(e:Exception) {
            android.util.Log.e("SourceTransferTest","${e.message}; ${e.stackTrace.take(5).joinToString()}")
            val shot=test.uiAutomation.takeScreenshot(); java.io.File(test.targetContext.getExternalFilesDir(null),"source-transfer-failure.png").outputStream().use { shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; shot.recycle()
            throw e
        } finally { test.runOnMainSync { activity.finish() }; prefs.edit().clear().commit(); sample.delete() }
    }
}
