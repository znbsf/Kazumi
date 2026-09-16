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
import kotlinx.coroutines.runBlocking
import org.kazumi.tv.data.*
import org.kazumi.tv.ui.*
import java.util.concurrent.atomic.AtomicInteger

object S4DetailsRegression {
    fun run(test: Instrumentation, live: Boolean) {
        val subject = if(live) runBlocking {
            val repo=CatalogRepository()
            repo.detail(repo.search("frieren").first { it.title == "葬送的芙莉莲" }.id)
        } else Subject(987654,"详情回归样本","",(1..60).joinToString("\n\n") { "第${it}段：完整简介阅读测试，保留段落和返回位置。" },
            SubjectMetadata("Original title","2023-09-29","TV",8.5,100,3,28))
        val attempts = AtomicInteger()
        val activity=test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        fun nodes(): List<AccessibilityNodeInfo> {
            val result=mutableListOf<AccessibilityNodeInfo>()
            fun visit(node: AccessibilityNodeInfo?) { if(node==null)return; result.add(node); for(i in 0 until node.childCount)visit(node.getChild(i)) }
            visit(test.uiAutomation.rootInActiveWindow)
            return result
        }
        fun find(text: String): AccessibilityNodeInfo {
            repeat(40) { nodes().firstOrNull { it.text?.toString()==text }?.let { node -> return node }; Thread.sleep(100) }
            error("Missing visible action: $text")
        }
        fun click(text: String) {
            var node: AccessibilityNodeInfo? = find(text)
            while(node != null && !node.isClickable)node=node.parent
            check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true)
            Thread.sleep(600)
        }
        try {
            test.runOnMainSync { activity.setContent { KazumiTheme(false) {
                Box(Modifier.fillMaxSize().background(KazumiColors.background).padding(30.dp)) {
                    DetailScreen(subject) {
                        if(!live && attempts.incrementAndGet()==1) error("controlled failure")
                        subject
                    }
                }
            } } }
            if(!live) {
                find("重试资料"); find("搜索播放来源")
                click("重试资料")
                check(attempts.get()==2)
                check(nodes().none { it.text?.toString()=="重试资料" })
                click("阅读完整简介")
                find("返回详情")
                repeat(5) { test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); Thread.sleep(150) }
                val readerImage=test.uiAutomation.takeScreenshot()
                java.io.File(test.targetContext.getExternalFilesDir(null),"detail-reader.png").outputStream().use {
                    readerImage.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                }
                readerImage.recycle()
                test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                find("阅读完整简介")
                check(nodes().none { it.text?.toString()=="返回详情" })
            }
            Thread.sleep(if(live) 3500 else 500)
            val screenshot=test.uiAutomation.takeScreenshot()
            java.io.File(test.targetContext.getExternalFilesDir(null),if(live) "detail-live.png" else "detail-fixture.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            screenshot.recycle()
        } finally { test.runOnMainSync { activity.finish() } }
    }
}
