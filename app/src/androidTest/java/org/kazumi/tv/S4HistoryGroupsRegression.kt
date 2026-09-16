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

object S4HistoryGroupsRegression {
    fun run(test: Instrumentation) {
        val context=object:ContextWrapper(test.targetContext) {
            override fun getSharedPreferences(name:String,mode:Int)=baseContext.getSharedPreferences("s4_history_groups_$name",0)
        }
        val raw=context.getSharedPreferences("tv_library",0)
        raw.edit().clear().commit()
        val original=test.targetContext.getSharedPreferences("tv_library",0).all.toMap()
        val store=LibraryStore(context)
        val subject=Subject(1,"同名节目","","")
        val a=HistoryEntry("a",subject,"第1集",11000,100000,PlaybackOrigin("RuleA","","",""),1000)
        val b=a.copy(key="b",episode="第2集",position=22000,origin=PlaybackOrigin("RuleB","","",""),updatedAt=3000,kind=HistoryKind.OFFLINE)
        val c=a.copy(key="c",subject=subject.copy(id=2),updatedAt=2000)
        raw.edit().putString("history",LibraryCodec.write(listOf(a,b,c).map(LibraryCodec::historyJson))).commit()
        val resumed=java.util.concurrent.atomic.AtomicReference<HistoryEntry>()
        var activity:MainActivity?=null
        try {
            activity=test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
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
                Box(Modifier.fillMaxSize().background(KazumiColors.background).padding(30.dp)) {
                    LibraryScreen("历史",onResume={ resumed.set(it) },onSelect={})
                }
            } } } }
            find("历史 · 3 / 3"); click("按日期"); find("按作品")
            click("继续最近观看"); check(resumed.get()==b)
            click("来源：全部"); find("历史 · 2 / 3")
            click("来源：在线"); find("历史 · 1 / 3")
            click("继续最近观看"); check(resumed.get()==b)
            click("管理记录"); click("全选"); click("删除所选（1）"); click("确认删除")
            find("历史 · 0 / 2"); check(store.history().map { it.key }.toSet()==setOf("a","c"))
            click("撤销上次删除"); find("历史 · 1 / 3"); check(store.history().first { it.key=="b" }==b)
            click("来源：缓存"); find("历史 · 3 / 3")
            val textNode=nodes().first { it.isEditable }
            check(textNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"RuleB")
            }))
            find("历史 · 1 / 3")
            val screenshot=test.uiAutomation.takeScreenshot()
            java.io.File(test.targetContext.getExternalFilesDir(null),"history-groups-fixture.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }; screenshot.recycle()
            check(original==test.targetContext.getSharedPreferences("tv_library",0).all)
        } finally { test.runOnMainSync { activity?.finish() }; raw.edit().clear().commit() }
    }
}
