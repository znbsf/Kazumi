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

object S4CollectionRegression {
    fun run(test: Instrumentation) {
        val context=object:ContextWrapper(test.targetContext) {
            override fun getSharedPreferences(name:String,mode:Int)=baseContext.getSharedPreferences("s4_collection_$name",0)
        }
        val raw=context.getSharedPreferences("tv_library",0)
        raw.edit().clear().commit()
        val original=test.targetContext.getSharedPreferences("tv_library",0).all.toMap()
        val store=LibraryStore(context)
        val a=Subject(987651,"收藏样本A","","")
        val b=Subject(987652,"收藏样本B","","")
        var activity:MainActivity?=null
        try {
            val legacy=org.json.JSONArray().put(LibraryCodec.subjectJson(a)).toString()
            raw.edit().putString("favorites",legacy).commit()
            check(store.collections().single().type==CollectionType.PLANNED)
            check(store.setCollection(b,CollectionType.WATCHED))
            check(raw.getString("favorites_migration_backup",null)==legacy)
            CollectionType.entries.forEach { type ->
                check(store.setCollection(a,type))
                check(LibraryStore(context).collections().first { it.subject.id==a.id }.type==type)
            }
            val history=HistoryEntry("fixture",a,"第1集",1000,10000)
            store.save(history)
            check(store.changeCollections(setOf(a.id,b.id),CollectionType.PLANNED)==2)
            val future="{\"version\":99,\"records\":[]}"
            raw.edit().putString("favorites",future).commit()
            check(!store.setCollection(a,CollectionType.WATCHING))
            check(raw.getString("favorites",null)==future)
            raw.edit().clear().commit(); store.setCollection(a,CollectionType.PLANNED); store.setCollection(b,CollectionType.WATCHED); store.save(history)
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
                Box(Modifier.fillMaxSize().background(KazumiColors.background).padding(30.dp)) { CollectionScreen {} }
            } } } }
            find("收藏 · 2 / 2")
            click("管理收藏"); click("选择当前结果"); click("修改所选（2）"); click("在看")
            find("已将 2 条收藏设为在看")
            check(store.collections().all { it.type==CollectionType.WATCHING })
            click("分类：全部"); find("分类：在看"); find("收藏 · 2 / 2")
            click("排序：最近变更"); find("排序：番剧名称")
            val screenshot=test.uiAutomation.takeScreenshot()
            java.io.File(test.targetContext.getExternalFilesDir(null),"collection-fixture.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            screenshot.recycle()
            click("管理收藏"); click("选择当前结果"); click("修改所选（2）"); click("取消收藏")
            click("返回管理"); check(store.collections().size==2)
            click("修改所选（2）"); click("取消收藏"); click("确认取消收藏")
            find("已取消 2 条收藏"); check(store.collections().isEmpty()); check(store.history().size==1)
            test.runOnMainSync { activity.setContent { CompositionLocalProvider(LocalContext provides context) { KazumiTheme(false) {
                Box(Modifier.fillMaxSize().background(KazumiColors.background).padding(30.dp)) { DetailScreen(a) { a } }
            } } } }
            click("收藏节目"); click("看过"); find("收藏 · 看过")
            check(store.collections().single().type==CollectionType.WATCHED)
            click("收藏 · 看过"); click("取消"); find("收藏 · 看过")
            check(original==test.targetContext.getSharedPreferences("tv_library",0).all)
        } finally {
            test.runOnMainSync { activity?.finish() }; raw.edit().clear().commit()
        }
    }
}
