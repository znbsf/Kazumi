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

object S4EpisodeBrowserRegression {
    fun run(test: Instrumentation) {
        val original=test.targetContext.getSharedPreferences("tv_library",0).all.toMap()
        val selected=java.util.concurrent.atomic.AtomicInteger(-1)
        val titles=(0..200).map { if(it==0) "特别篇" else "第${it}集" }
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

            test.runOnMainSync { activity.setContent { KazumiTheme(false) {
                Box(Modifier.fillMaxSize().background(KazumiColors.background).padding(30.dp)) {
                    EpisodeBrowser(titles,99,setOf(98),Modifier.fillMaxSize(),restoreIndex=99) { selected.set(it) }
                }
            } } }
            click("100. 当前 · 第99集"); check(selected.get()==99)
            click("正序"); click("定位当前"); click("100. 当前 · 第99集"); check(selected.get()==99)
            fun setNumber(value:String) {
                check(nodes().first { it.isEditable }.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)
                }))
            }
            setNumber("1"); click("定位序号"); click("1. 特别篇"); check(selected.get()==0)
            setNumber("202"); click("定位序号"); find("请输入1至201的列表序号")
            setNumber("100"); click("定位序号"); find("100. 当前 · 第99集")
            val screenshot=test.uiAutomation.takeScreenshot()
            java.io.File(test.targetContext.getExternalFilesDir(null),"episodes-fixture.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }; screenshot.recycle()
            check(original==test.targetContext.getSharedPreferences("tv_library",0).all)
        } finally { test.runOnMainSync { activity.finish() } }
    }
}
