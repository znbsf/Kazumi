package org.kazumi.tv

import android.app.Instrumentation
import android.os.Bundle
import kotlinx.coroutines.runBlocking
import org.kazumi.tv.rules.*
import org.kazumi.tv.playback.*

/** Uses installed rules without editing them. Never prints media URLs, cookies or response bodies. */
object RealSourceRegression {
    fun run(test: Instrumentation, pagesOnly:Boolean=false,play:Boolean=false) = runBlocking {
        fun report(value:String) { test.sendStatus(0,Bundle().apply { putString("stream",value+"\n") }) }
        val repo=RuleRepository(test.targetContext)
        for(name in listOf("sorani","gugu3","moonci","mutefun")) {
            val rule=repo.rules.firstOrNull { it.name.equals(name,true) } ?: continue
            var stage="search"
            try {
                val results=repo.search(rule,"无职转生")
                report("$name search=${results.size}; legacy=${rule.json.optBoolean("useLegacyParser")}; captcha=${rule.json.optJSONObject("antiCrawlerConfig")?.optInt("captchaType")}")
                val match=results.firstOrNull { it.title.contains("第三季") }
                if(match==null) { report("$name third_season_not_found"); continue }
                stage="chapters"
                val roads=repo.chapters(rule,match)
                report("$name title=${match.title}; roads=${roads.size}")
                for((index,road) in roads.take(2).withIndex()) {
                    report("$name road=$index episodes=${road.episodes.size}")
                    val episode=road.episodes.firstOrNull { Regex("(?:第)?0?12(?:集|话|$)").containsMatchIn(it.title) } ?: road.episodes.getOrNull(11)
                    if(episode==null) { report("$name episode12_missing_in_source");continue }
                    stage="resolve road=$index"
                    report("$name road=$index episode=${episode.title}")
                    if(pagesOnly) {
                        val html=org.kazumi.tv.data.HttpText.requestAsync(episode.pageUrl,headers=mapOf("User-Agent" to rule.userAgent,"Referer" to rule.referer))
                        val folder=java.io.File(test.targetContext.getExternalFilesDir(null),"source-diagnosis").apply { mkdirs() }
                        java.io.File(folder,"$name-$index.json").writeText(org.json.JSONObject().put("rule",rule.json).put("pageUrl",episode.pageUrl).put("html",html).toString())
                        report("$name page_saved bytes=${html.length}")
                        continue
                    }
                    try {
                        val request=WebMediaResolver(test.targetContext,diagnostic={ report("$name $it") }).resolve(episode.pageUrl,rule,episode.title)
                        report("$name road=$index resolved mime=${request.mimeType}; host=${java.net.URI(request.url).host}")
                        if(play) { play(test,request,"$name-$index");report("$name road=$index first_frame, playback_advance, seek60, pause_resume=OK") }
                    } catch(e:Exception) { report("$name road=$index failure=${e.javaClass.simpleName}"+if(e is MediaResolutionFailure)e.message else "") }
                }
            } catch(e:Exception) {
                report("$name stage=$stage failure=${e.javaClass.simpleName}" + if(e is MediaResolutionFailure) ": ${e.message}" else "")
                if(pagesOnly&&e is SourceVerificationRequired) {
                    val html=org.kazumi.tv.data.HttpText.requestAsync(e.pageUrl,e.method,mapOf("User-Agent" to rule.userAgent,"Referer" to rule.referer),e.body)
                    val folder=java.io.File(test.targetContext.getExternalFilesDir(null),"source-diagnosis").apply { mkdirs() }
                    java.io.File(folder,"$name-challenge.json").writeText(org.json.JSONObject().put("rule",rule.json).put("pageUrl",e.pageUrl).put("html",html).toString())
                }
            }
        }
    }
    private fun play(test:Instrumentation,request:PlaybackRequest,name:String) {
        val activity=test.startActivitySync(android.content.Intent(test.targetContext,MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        val frame=java.util.concurrent.atomic.AtomicBoolean()
        val error=java.util.concurrent.atomic.AtomicReference<String?>()
        var engine:NativePlayer?=null
        fun await(label:String,timeout:Long=35000,predicate:()->Boolean) {
            val end=System.currentTimeMillis()+timeout
            while(System.currentTimeMillis()<end) { error.get()?.let { kotlin.error(it) };if(predicate())return;Thread.sleep(100) }
            kotlin.error("timeout $label")
        }
        fun position():Long { var value=0L;test.runOnMainSync { value=engine!!.player.currentPosition };return value }
        try {
            test.runOnMainSync {
                val texture=android.view.TextureView(activity);activity.setContentView(texture)
                engine=NativePlayer(activity,request.url)
                engine!!.player.addListener(object:androidx.media3.common.Player.Listener {
                    override fun onRenderedFirstFrame() { frame.set(true) }
                    override fun onPlayerError(failure:androidx.media3.common.PlaybackException) { error.set("player code=${failure.errorCode}") }
                })
                engine!!.player.setVideoTextureView(texture);engine!!.open(request)
            }
            await("frame") { frame.get() }
            await("advance") { position()>5000 }
            test.runOnMainSync { engine!!.player.seekTo(60000) }
            await("seek") { position()>62000 }
            test.runOnMainSync { engine!!.player.pause() };val paused=position();Thread.sleep(1000);check(kotlin.math.abs(position()-paused)<300)
            test.runOnMainSync { engine!!.player.play() };await("resume") { position()>paused+2000 }
            val bitmap=test.uiAutomation.takeScreenshot()
            java.io.File(test.targetContext.getExternalFilesDir(null),"real-play-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        } finally { test.runOnMainSync { engine?.release();activity.finish() } }
    }
}
