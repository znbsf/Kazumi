package org.kazumi.tv
import android.app.Instrumentation
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kazumi.tv.data.*
import org.kazumi.tv.ui.*

object S4RelationsRegression {
    fun run(test:Instrumentation,live:Boolean) {
        val root=Subject(if(live)400602 else 101,if(live) "葬送的芙莉莲" else "关联根作品","", "fixture synopsis")
        val attempts=java.util.concurrent.atomic.AtomicInteger()
        val activity=test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            fun nodes():List<AccessibilityNodeInfo> {
                val result=mutableListOf<AccessibilityNodeInfo>()
                fun visit(node:AccessibilityNodeInfo?) { if(node==null)return; result.add(node); for(i in 0 until node.childCount)visit(node.getChild(i)) }
                visit(test.uiAutomation.rootInActiveWindow); return result
            }
            fun find(text:String):AccessibilityNodeInfo {
                repeat(100) { nodes().firstOrNull { it.text?.toString()==text }?.let { return it }; Thread.sleep(100) }
                error("Missing $text")
            }
            fun click(text:String) {
                var node:AccessibilityNodeInfo?=find(text)
                while(node!=null && !node.isClickable)node=node.parent
                check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true); Thread.sleep(400)
            }
            test.runOnMainSync { activity.setContent { KazumiTheme(false) {
                Box(Modifier.fillMaxSize().background(KazumiColors.background).padding(30.dp)) {
                    DetailScreen(root,loadDetail={ if(live)CatalogRepository().detail(it) else Subject(it,if(it==101) "关联根作品" else "关联子作品","","fixture synopsis") },loadRelations={ id ->
                        if(live)RelationRepository().load(id) else {
                            if(attempts.incrementAndGet()==1)error("fixture failure")
                            RelationResult(listOf(SubjectRelation("续集",Subject(102,"关联子作品","",""))),false)
                        }
                    })
                }
            } } }
            click("关联动画")
            if(!live) {
                click("关联读取失败，重试"); click("续集 · 关联子作品")
                find("关联子作品"); find("搜索播放来源"); find("返回上个作品")
                test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                find("续集 · 关联子作品"); find("返回作品详情")
                click("返回作品详情"); find("关联根作品"); find("搜索播放来源")
            } else {
                repeat(90) {
                    if(nodes().any { it.text?.toString()?.startsWith("续集 ·")==true })return@repeat
                    Thread.sleep(500)
                }
                check(nodes().any { it.text?.toString()?.startsWith("续集 ·")==true }) { "No live sequel" }
                Thread.sleep(22000)
            }
            val screenshot=test.uiAutomation.takeScreenshot()
            java.io.File(test.targetContext.getExternalFilesDir(null),if(live) "relations-live.png" else "relations-fixture.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; screenshot.recycle()
        } finally { test.runOnMainSync { activity.finish() } }
    }
}
