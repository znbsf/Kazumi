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

object S4SetupRegression {
    fun run(test: Instrumentation) {
        val context=object:ContextWrapper(test.targetContext) {
            override fun getSharedPreferences(name:String,mode:Int)=baseContext.getSharedPreferences("s4_setup_$name",0)
        }
        val raw=context.getSharedPreferences("tv_rules",0); val settings=context.getSharedPreferences("tv_settings",0)
        raw.edit().clear().commit(); settings.edit().clear().commit()
        val original=test.targetContext.getSharedPreferences("tv_rules",0).all.toMap()
        val store=org.kazumi.tv.rules.RuleStore(context)
        store.all().forEach { if(store.isEnabled(it))store.toggle(it) }
        val json=org.json.JSONObject(context.assets.open("rules/7sefun.json").bufferedReader().use { it.readText() }).put("version","fixture-next")
        val name=json.getString("name")
        val loads=java.util.concurrent.atomic.AtomicInteger(); val installs=java.util.concurrent.atomic.AtomicInteger()
        val completed=java.util.concurrent.atomic.AtomicInteger(); val cancelled=java.util.concurrent.atomic.AtomicInteger()
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
                SetupScreen(onCancel={ cancelled.incrementAndGet() },catalogContent={
                    RuleCatalogScreen(load={
                        if(loads.incrementAndGet()==1)error("controlled directory failure")
                        listOf(org.kazumi.tv.rules.CatalogRule(name,"fixture-next"))
                    },install={ _,target ->
                        if(installs.incrementAndGet()==1)error("controlled install failure")
                        target.importJson(json.toString())
                    })
                }) { TvPreferences(context).setupComplete=true; completed.incrementAndGet() }
            } } } }
            click("继续"); click("继续")
            repeat(40) { if(nodes().any { it.text?.contains("规则目录连接失败")==true })return@repeat; Thread.sleep(50) }
            click("刷新目录")
            fun installAction() {
                val label=nodes().mapNotNull { it.text?.toString() }.first { it.startsWith("$name  fixture-next") }
                click(label)
            }
            installAction()
            check(installs.get()==1)
            check(store.all().first { it.name==name }.json.optString("version")!="fixture-next")
            installAction(); check(installs.get()==2)
            check(store.all().first { it.name==name }.json.optString("version")=="fixture-next")
            click("继续"); find("仅浏览，稍后添加来源")
            val screenshot=test.uiAutomation.takeScreenshot()
            java.io.File(test.targetContext.getExternalFilesDir(null),"setup-summary.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            screenshot.recycle()
            check(!TvPreferences(context).setupComplete)
            click("仅浏览，稍后添加来源"); check(TvPreferences(context).setupComplete && completed.get()==1)
            click("上一步"); click("上一步"); click("上一步"); click("返回设置")
            check(cancelled.get()==1 && TvPreferences(context).setupComplete)
            check(original==test.targetContext.getSharedPreferences("tv_rules",0).all)
        } finally {
            test.runOnMainSync { activity.finish() }; raw.edit().clear().commit(); settings.edit().clear().commit()
        }
    }
}
