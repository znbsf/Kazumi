package org.kazumi.tv
import android.app.Instrumentation
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import org.kazumi.tv.data.*
import org.kazumi.tv.playback.*
import org.kazumi.tv.ui.*

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object SleepTimerRegression {
    fun run(test:Instrumentation) {
        val context=object:ContextWrapper(test.targetContext) { override fun getSharedPreferences(name:String,mode:Int)=baseContext.getSharedPreferences("sleep_fixture_$name",0) }
        val prefs=context.getSharedPreferences("tv_settings",0); prefs.edit().clear().commit()
        TvPreferences(context).incognito=true; TvPreferences(context).danmakuEnabled=false
        val sample=java.io.File(test.targetContext.cacheDir,"sleep-fixture.mp4")
        test.context.assets.open("tracks-fixture.mp4").use { input -> sample.outputStream().use { input.copyTo(it) } }
        val request=PlaybackRequest(sample.toURI().toString(),emptyMap(),"Sleep fixture")
        val activity=test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        lateinit var timer:PlaybackSleepTimer
        var engine:NativePlayer?=null
        fun awaitState(label:String,predicate:()->Boolean) {
            repeat(70) { var pass=false; test.runOnMainSync { pass=predicate() }; if(pass)return; Thread.sleep(100) }
            error("Timed out: $label")
        }
        fun open() { test.runOnMainSync { engine?.release(); engine=NativePlayer(context,sample.toURI().toString(),sleepTimer=timer); engine!!.player.volume=0f; engine!!.open(request) } }
        try {
            test.runOnMainSync { timer=PlaybackSleepTimer() }
            open(); awaitState("initial playing") { engine!!.player.isPlaying }
            test.runOnMainSync { timer.start(2500) }
            Thread.sleep(700); open()
            awaitState("replacement playing") { engine!!.player.isPlaying }
            awaitState("expiry pauses replacement") { timer.state.value.expired && !engine!!.player.playWhenReady }
            open(); test.runOnMainSync { check(!engine!!.player.playWhenReady) }
            test.runOnMainSync { engine!!.player.play() }
            awaitState("manual play clears expiry") { engine!!.player.isPlaying && !timer.state.value.expired }
            test.runOnMainSync { timer.start(500); timer.cancel() }
            Thread.sleep(900); test.runOnMainSync { check(engine!!.player.playWhenReady && !timer.state.value.expired); engine!!.release(); engine=null }
            test.runOnMainSync { activity.setContent { CompositionLocalProvider(LocalContext provides context) { KazumiTheme(false) {
                PlayerScreen(request,Subject(991,"定时测试","",""),sleepTimer=timer,onClose={})
            } } } }
            Thread.sleep(1500); test.runOnMainSync { timer.start(1000) }
            fun hasText():Boolean {
                fun visit(node:android.view.accessibility.AccessibilityNodeInfo?):Boolean {
                    if(node==null)return false
                    if(node.text?.toString()=="定时时间已到，视频已暂停。")return true
                    return (0 until node.childCount).any { visit(node.getChild(it)) }
                }
                return visit(test.uiAutomation.rootInActiveWindow)
            }
            repeat(40) { if(!hasText())Thread.sleep(100) }
            check(hasText()) { "Expiry UI missing" }
            val shot=test.uiAutomation.takeScreenshot()
            java.io.File(test.targetContext.getExternalFilesDir(null),"sleep-expired.png").outputStream().use { shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; shot.recycle()
        } finally {
            test.runOnMainSync { engine?.release(); timer.cancel(); activity.finish() }
            prefs.edit().clear().commit()
        }
    }
}
