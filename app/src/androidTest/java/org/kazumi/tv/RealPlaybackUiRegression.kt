package org.kazumi.tv

import android.app.Instrumentation
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kazumi.tv.data.*
import org.kazumi.tv.rules.*
import org.kazumi.tv.ui.*

/** Real search -> real chapters -> production resolver/player -> back/focus -> resume.
 * A unique fixture source avoids colliding with user history; the original store is restored after disposal.
 */
object RealPlaybackUiRegression {
    fun run(test:Instrumentation) {
        val context=test.targetContext
        removeFixtureHistory(context)
        val actual=context.getSharedPreferences("tv_library",0).all.toMap()
        val library=LibraryStore(context)
        check(!TvPreferences(context).incognito) { "history acceptance needs incognito disabled" }
        val repo=RuleRepository(test.targetContext)
        val catalog=object:SourceCatalog {
            override val rules=repo.rules.filter { it.name.equals("sorani",true) }.map { SourceRule(org.json.JSONObject(it.json.toString()).put("name","UI验证-sorani")) }
            override suspend fun search(rule:SourceRule,keyword:String)=repo.search(rule,keyword)
            override suspend fun chapters(rule:SourceRule,match:SourceMatch)=repo.chapters(rule,match)
        }
        check(catalog.rules.size==1)
        val activity=test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        fun nodes():List<AccessibilityNodeInfo> {
            val values=mutableListOf<AccessibilityNodeInfo>();fun walk(n:AccessibilityNodeInfo?) { if(n==null)return;values.add(n);for(i in 0 until n.childCount)walk(n.getChild(i)) };walk(test.uiAutomation.rootInActiveWindow);return values
        }
        fun await(label:String,timeout:Long=60000,predicate:()->Boolean) { val end=System.currentTimeMillis()+timeout;while(System.currentTimeMillis()<end) { if(predicate())return;Thread.sleep(200) };error("timeout $label") }
        fun click(matcher:(String)->Boolean) {
            await("click target") { nodes().any { matcher(it.text?.toString().orEmpty()) } }
            var node=nodes().first { matcher(it.text?.toString().orEmpty()) }
            while(!node.isClickable)node=node.parent
            check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));Thread.sleep(300)
        }
        fun screenshot(name:String) { val image=test.uiAutomation.takeScreenshot();java.io.File(test.targetContext.getExternalFilesDir(null),name).outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };image.recycle() }
        try {
            test.runOnMainSync { activity.setContent { CompositionLocalProvider(LocalContext provides context) { KazumiTheme(false) {
                Box(Modifier.fillMaxSize().padding(24.dp)) { SourceScreen(Subject(99000916,"无职转生","",""),catalog=catalog) }
            } } } }
            click { it.contains("第三季") }
            click { it.contains("12.")&&it.contains("第12集") }
            await("real rendered frame progress",90000) { library.history().any { it.subject.id==99000916&&it.episode.contains("第12集")&&it.position>=5000&&it.duration>60000 } }
            screenshot("real-source-ui-playing.png")
            // Back may first hide the controls; continue only while the expected player window is still active.
            repeat(2) {
                if(nodes().none { it.text?.toString()=="返回匹配结果" }) { test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);Thread.sleep(1500) }
            }
            test.sendStatus(0,android.os.Bundle().apply { putString("stream","UI after back: "+nodes().mapNotNull { it.text?.toString() }.take(35).joinToString(" | ")+"\n") })
            await("return to episodes") { nodes().any { it.text?.toString()=="返回匹配结果" } }
            val saved=library.history().first { it.subject.id==99000916&&it.episode.contains("第12集") }.position
            check(saved>=5000)
            screenshot("real-source-ui-return.png")
            click { it.contains("12.")&&it.contains("第12集") }
            await("resume progress",90000) { library.history().any { it.subject.id==99000916&&it.episode.contains("第12集")&&it.position>saved+2000 } }
        } catch(failure:Exception) { screenshot("real-source-ui-failure.png");throw failure } finally {
            test.runOnMainSync { activity.setContent { Box(Modifier) };activity.finish() }
            test.waitForIdleSync()
            val prefs=context.getSharedPreferences("tv_library",0)
            val edit=prefs.edit().clear()
            for((key,value) in actual)when(value) {
                is String -> edit.putString(key,value)
                is Boolean -> edit.putBoolean(key,value)
                is Int -> edit.putInt(key,value)
                is Long -> edit.putLong(key,value)
                is Float -> edit.putFloat(key,value)
                is Set<*> -> { @Suppress("UNCHECKED_CAST") edit.putStringSet(key,value as Set<String>) }
            }
            check(edit.commit());check(actual==prefs.all)
        }
    }
    fun removeFixtureHistory(context:android.content.Context) {
        val prefs=context.getSharedPreferences("tv_library",0)
        val edit=prefs.edit()
        for(key in listOf("history","history_previous")) {
            val raw=prefs.getString(key,null) ?: continue
            val parsed=org.json.JSONTokener(raw).nextValue()
            val array=if(parsed is org.json.JSONObject)parsed.getJSONArray("records") else parsed as org.json.JSONArray
            var changed=false
            for(i in array.length()-1 downTo 0)if(array.optJSONObject(i)?.optInt("id")==99000916) { array.remove(i);changed=true }
            if(changed)edit.putString(key,parsed.toString())
        }
        check(edit.commit())
    }
}
