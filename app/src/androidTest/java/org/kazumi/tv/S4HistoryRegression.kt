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

object S4HistoryRegression {
    fun run(test: Instrumentation) {
        val context=object:ContextWrapper(test.targetContext) {
            override fun getSharedPreferences(name: String,mode: Int)=baseContext.getSharedPreferences("s4_history_$name",Context.MODE_PRIVATE)
        }
        val raw=context.getSharedPreferences("tv_library",0)
        val settings=context.getSharedPreferences("tv_settings",0)
        raw.edit().clear().commit(); settings.edit().clear().commit()
        val original=test.targetContext.getSharedPreferences("tv_library",0).all.toMap()
        val store=LibraryStore(context)
        val subject=Subject(1,"管理样本","","")
        val a=HistoryEntry("a",subject,"第一集",1000,100000)
        val b=HistoryEntry("b",subject,"第二集",2000,100000)
        var activity:MainActivity?=null
        try {
            store.save(a); store.save(b)
            val before=raw.all.toMap()
            TvPreferences(context).incognito=true
            store.save(a.copy(position=50000)); store.save(b.copy(key="new"))
            check(raw.all==before) { "Incognito wrote progress or backups" }
            TvPreferences(context).incognito=false
            check(store.deleteHistory(setOf("a"))==1)
            check(LibraryStore(context).deletedHistoryCount()==1)
            store.save(a.copy(position=7000))
            check(store.undoHistoryDeletion()==0 && store.history().first { it.key=="a" }.position==7000L)
            check(store.deleteHistory(setOf("a","b"))==2)
            check(store.history().isEmpty())
            check(LibraryStore(context).undoHistoryDeletion()==2)
            check(store.history().first { it.key=="a" }.position==7000L)
            val future="{\"version\":99,\"records\":[]}"
            raw.edit().putString("history",future).commit()
            check(store.deleteHistory(setOf("a","b"))==0)
            check(raw.getString("history",null)==future)
            raw.edit().clear().commit(); store.save(a); store.save(b)

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
            test.runOnMainSync { activity.setContent { CompositionLocalProvider(LocalContext provides context) {
                KazumiTheme(false) { Box(Modifier.fillMaxSize().background(KazumiColors.background).padding(30.dp)) {
                    LibraryScreen("历史",onResume={ error("Management launched playback") },onSelect={})
                } }
            } } }
            click("管理记录"); click("全选"); click("删除所选（2）")
            find("确认删除"); click("取消")
            check(store.history().size==2)
            click("删除所选（2）"); click("确认删除")
            find("已删除 2 条记录"); check(store.history().isEmpty())
            click("撤销上次删除")
            find("已恢复 2 条记录"); check(store.history().size==2)
            check(original==test.targetContext.getSharedPreferences("tv_library",0).all)
        } finally {
            test.runOnMainSync { activity?.finish() }
            raw.edit().clear().commit(); settings.edit().clear().commit()
        }
    }
}
