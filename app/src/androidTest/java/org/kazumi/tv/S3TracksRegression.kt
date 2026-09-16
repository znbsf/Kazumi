package org.kazumi.tv

import android.app.Instrumentation
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import org.kazumi.tv.data.TvPreferences
import org.kazumi.tv.playback.*
import org.kazumi.tv.ui.*

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object S3TracksRegression {
    fun run(test:Instrumentation) {
        val context=object:ContextWrapper(test.targetContext) {
            override fun getSharedPreferences(name:String,mode:Int)=baseContext.getSharedPreferences("s3_tracks_$name",0)
        }
        val raw=context.getSharedPreferences("tv_settings",0)
        raw.edit().clear().commit()
        val original=test.targetContext.getSharedPreferences("tv_settings",0).all.toMap()
        val sample=java.io.File(test.targetContext.cacheDir,"s3-tracks-fixture.mp4")
        test.context.assets.open("tracks-fixture.mp4").use { input -> sample.outputStream().use { input.copyTo(it) } }
        val preferences=TvPreferences(context)
        val activity=test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        var engine:NativePlayer?=null
        var frame=false
        var cues=""
        val picture=mutableStateOf(PictureMode.FIT)
        fun awaitState(label:String,predicate:()->Boolean) {
            repeat(100) { var ready=false; test.runOnMainSync { ready=predicate() }; if(ready)return; Thread.sleep(100) }
            var details=""
            test.runOnMainSync { details="cue=$cues tracks="+engine!!.player.currentTracks.groups.joinToString { group ->
                "${group.type}:"+(0 until group.length).joinToString { i -> "${group.getTrackFormat(i).language}/${group.isTrackSelected(i)}" }
            } }
            error("Timed out: $label; $details")
        }
        fun open() {
            test.runOnMainSync {
                engine?.release(); frame=false; cues=""
                engine=NativePlayer(context,sample.toURI().toString())
                engine!!.player.volume=0f
                engine!!.player.addListener(object:Player.Listener {
                    override fun onRenderedFirstFrame() { frame=true }
                    override fun onCues(cueGroup:CueGroup) { cues=cueGroup.cues.joinToString { it.text.toString() } }
                })
                activity.setContent { KazumiTheme(false) { PlaybackVideoSurface(engine!!.player,false,picture.value) } }
                engine!!.open(PlaybackRequest(sample.toURI().toString(),emptyMap(),"Generated tracks fixture"),1000)
            }
            awaitState("video first frame") { frame && engine!!.player.isPlaying }
        }
        fun languages(type:Int)=engine!!.player.currentTracks.groups.filter { it.type==type }.flatMap { group ->
            (0 until group.length).filter { group.isTrackSelected(it) }.map { group.getTrackFormat(it).language.orEmpty() }
        }
        try {
            preferences.audioLanguage="ja"; preferences.textLanguage="ja"; preferences.subtitles=true
            open()
            awaitState("Japanese audio and visible Japanese subtitle cue") {
                languages(C.TRACK_TYPE_AUDIO).any { it in listOf("ja","jpn") } && cues.contains("JAPANESE FIXTURE")
            }
            for(mode in PictureMode.entries) {
                test.runOnMainSync { picture.value=mode }; Thread.sleep(500)
                val screenshot=test.uiAutomation.takeScreenshot()
                java.io.File(test.targetContext.getExternalFilesDir(null),"picture-${mode.name}.png").outputStream().use {
                    screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                }; screenshot.recycle()
            }
            preferences.subtitles=false; open()
            awaitState("subtitles disabled after new engine") { engine!!.player.currentPosition>1800 && languages(C.TRACK_TYPE_TEXT).isEmpty() && cues.isEmpty() }
            preferences.audioLanguage="ko"; preferences.textLanguage="ko"; preferences.subtitles=true; open()
            awaitState("unavailable language falls back to default English tracks") {
                languages(C.TRACK_TYPE_AUDIO).any { it in listOf("en","eng") } && cues.contains("ENGLISH FIXTURE")
            }
            preferences.audioLanguage=null; preferences.textLanguage=null; open()
            awaitState("automatic default tracks") {
                languages(C.TRACK_TYPE_AUDIO).any { it in listOf("en","eng") } && cues.contains("ENGLISH FIXTURE")
            }
            check(original==test.targetContext.getSharedPreferences("tv_settings",0).all)
        } finally {
            test.runOnMainSync { activity.finish(); engine?.release() }
            sample.delete(); raw.edit().clear().commit()
        }
    }
}
