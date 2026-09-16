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

object S4CreditsRegression {
    fun run(test:Instrumentation,live:Boolean) {
        val root=Subject(if(live)400602 else 101,if(live) "葬送的芙莉莲" else "关联根作品","", "fixture synopsis")
        val attempts=java.util.concurrent.atomic.AtomicInteger()
        val detailAttempts=java.util.concurrent.atomic.AtomicInteger()
        val actor=CreditEntry(2,"测试配音","",summary="配音简介")
        val character=CreditEntry(1,"测试角色","",job="主角",summary="角色简介",character=true,actors=listOf(actor))
        val staff=CreditEntry(3,"测试人员","",job="导演",episodes="1-12")
        val realCharacters=if(live)kotlinx.coroutines.runBlocking { CreditsRepository().list(400602,true) } else emptyList()
        val realStaff=if(live)kotlinx.coroutines.runBlocking { CreditsRepository().list(400602,false) } else emptyList()
        if(live)check(realCharacters.isNotEmpty() && realStaff.isNotEmpty())
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
                    CreditsScreen(root,load={ _,characters ->
                        if(live) { if(characters)realCharacters else realStaff } else {
                            if(attempts.incrementAndGet()==1)error("fixture failure")
                            if(characters)listOf(character) else listOf(staff)
                        }
                    },loadDetail={ entry -> if(detailAttempts.incrementAndGet()==1)error("detail failure"); entry },onBack={})
                }
            } } }
            if(!live) {
                click("名单加载失败，重试"); click("测试角色")
                click("资料加载失败，重试"); find("角色简介")
                click("配音 · 测试配音"); find("配音简介"); find("返回角色资料")
                test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); find("测试角色")
                test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); find("✓ 角色与配音")
                click("制作人员"); find("测试人员"); find("参与集数 1-12")
                val input=nodes().first { it.className?.toString()=="android.widget.EditText" }
                check(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"不存在") }))
                find("没有符合筛选的资料")
                click("角色与配音"); find("测试角色")
            } else {
                find(realCharacters.first().name)
                Thread.sleep(22000)
            }
            val screenshot=test.uiAutomation.takeScreenshot()
            java.io.File(test.targetContext.getExternalFilesDir(null),if(live) "credits-live.png" else "credits-fixture.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; screenshot.recycle()
            if(live) {
                click("制作人员"); find(realStaff.first().name)
                Thread.sleep(1000)
                val staffShot=test.uiAutomation.takeScreenshot()
                java.io.File(test.targetContext.getExternalFilesDir(null),"credits-staff-live.png").outputStream().use { staffShot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; staffShot.recycle()
            }
        } finally { test.runOnMainSync { activity.finish() } }
    }
}
