package org.kazumi.tv

import android.app.Instrumentation
import android.content.Context
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
import org.kazumi.tv.data.*
import org.kazumi.tv.ui.*

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object S3PlaybackOptionsRegression {
    fun run(test: Instrumentation) {
        val context=object:ContextWrapper(test.targetContext) {
            override fun getSharedPreferences(name:String,mode:Int)=baseContext.getSharedPreferences("s3_options_$name",0)
        }
        val raw=context.getSharedPreferences("tv_settings",0)
        raw.edit().clear().commit()
        val original=test.targetContext.getSharedPreferences("tv_settings",0).all.toMap()
        val preferences=TvPreferences(context)
        var engine:org.kazumi.tv.playback.NativePlayer?=null
        val activity=test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            fun nodes():List<AccessibilityNodeInfo> {
                val result=mutableListOf<AccessibilityNodeInfo>()
                fun visit(node:AccessibilityNodeInfo?) { if(node==null)return; result.add(node); for(i in 0 until node.childCount)visit(node.getChild(i)) }
                visit(test.uiAutomation.rootInActiveWindow); return result
            }
            fun find(text:String):AccessibilityNodeInfo {
                repeat(40) { nodes().firstOrNull { it.text?.toString()==text }?.let { node -> return node }; Thread.sleep(100) }
                error("Missing $text; visible="+nodes().mapNotNull { it.text?.toString() }.joinToString(" | "))
            }
            fun click(text:String) {
                var node:AccessibilityNodeInfo?=find(text)
                while(node!=null && !node.isClickable)node=node.parent
                check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true); Thread.sleep(500)
            }

            test.runOnMainSync { activity.setContent { CompositionLocalProvider(LocalContext provides context) { KazumiTheme(false) {
                Box(Modifier.fillMaxSize().background(KazumiColors.background).padding(20.dp)) { PlaybackPreferencesPanel() }
            } } } }
            click("默认倍速：1.0×"); find("默认倍速：1.25×")
            click("画面比例：自动"); find("画面比例：裁切填充")
            click("音轨语言：自动"); find("音轨语言：日语")
            click("字幕：开启"); find("字幕：关闭")
            click("字幕语言：自动"); find("字幕语言：日语")
            check(TvPreferences(context).speed==1.25f && TvPreferences(context).audioLanguage=="ja")
            // Exercise lower controls after scrolling rather than assuming they are visible.
            repeat(8) { test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN); Thread.sleep(80) }
            click("自动续播：开启"); find("自动续播：关闭")
            click("快进退：10秒"); find("快进退：15秒")
            click("控制栏隐藏：5秒"); find("控制栏隐藏：6秒")
            test.runOnMainSync {
                engine=org.kazumi.tv.playback.NativePlayer(context)
                check(engine!!.player.playbackParameters.speed==1.25f)
                check(engine!!.player.seekBackIncrement==15000L && engine!!.player.seekForwardIncrement==15000L)
                val tracks=engine!!.player.trackSelectionParameters
                check(tracks.preferredAudioLanguages.contains("ja") && tracks.preferredTextLanguages.contains("ja"))
                check(androidx.media3.common.C.TRACK_TYPE_TEXT in tracks.disabledTrackTypes)
                check(tracks.overrides.isEmpty())
                engine!!.release(); engine=org.kazumi.tv.playback.NativePlayer(context)
                check(engine!!.player.playbackParameters.speed==1.25f)
                activity.setContent { KazumiTheme(false) { PlaybackVideoSurface(engine!!.player,false,org.kazumi.tv.playback.PictureMode.FOUR_THREE) } }
            }
            Thread.sleep(600)
            test.runOnMainSync {
                fun findView(view:android.view.View):androidx.media3.ui.PlayerView? {
                    if(view is androidx.media3.ui.PlayerView)return view
                    if(view is android.view.ViewGroup)for(i in 0 until view.childCount)findView(view.getChildAt(i))?.let { return it }
                    return null
                }
                val view=findView(activity.window.decorView) ?: error("Missing PlayerView")
                check(view.width>0 && kotlin.math.abs(view.width.toFloat()/view.height-4f/3f)<.01f)
                check(view.resizeMode==androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL)
            }
            check(!preferences.resumePlayback && preferences.controlsSeconds==6)
            check(original==test.targetContext.getSharedPreferences("tv_settings",0).all)
        } finally {
            test.runOnMainSync { activity.finish(); engine?.release() }; raw.edit().clear().commit()
        }
    }
}
