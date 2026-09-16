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

object S4CalendarRegression {
    fun run(test:Instrumentation,live:Boolean,seasonLive:Boolean=false) {
        val context=object:ContextWrapper(test.targetContext) {
            override fun getSharedPreferences(name:String,mode:Int)=baseContext.getSharedPreferences("s4_calendar_$name",0)
        }
        val raw=context.getSharedPreferences("tv_library",0); val settings=context.getSharedPreferences("tv_settings",0)
        raw.edit().clear().commit(); settings.edit().clear().commit()
        val a=Subject(1,"排期样本A","",""); val b=Subject(2,"排期样本B","","")
        LibraryStore(context).setCollection(a,CollectionType.WATCHING)
        val seasonAttempts=java.util.concurrent.atomic.AtomicInteger()
        val attempts=java.util.concurrent.atomic.AtomicInteger(); val selected=java.util.concurrent.atomic.AtomicInteger()
        val schedule=if(live)kotlinx.coroutines.runBlocking { CalendarRepository().load(true,if(seasonLive) AnimeSeason.current().shift(-1) else null) } else WeekSchedule(List(7) { listOf(a,b) })
        if(live)check(schedule.days.sumOf { it.size }>0)
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
                Box(Modifier.fillMaxSize().background(KazumiColors.background).padding(30.dp)) {
                    CalendarScreen(load={ _,season ->
                        if(!live && attempts.incrementAndGet()==1)error("fixture failure")
                        if(!live && season!=null) {
                            if(seasonAttempts.incrementAndGet()==1) { kotlinx.coroutines.delay(700); error("season offline") }
                            WeekSchedule(List(7) { listOf(Subject(3,"上季样本","","")) })
                        } else schedule
                    },onSelect={ selected.set(it.id) })
                }
            } } } }
            if(!live) {
                click("排期读取失败，重试"); find("排期样本A"); find("排期样本B")
                click("只看在追：关"); find("排期样本A")
                check(nodes().none { it.text?.toString()=="排期样本B" })
                click("排期样本A"); check(selected.get()==1)
                click("只看在追：开")
                click("上一季")
                check(nodes().none { it.text?.toString()=="排期样本A" })
                click("排期读取失败，重试"); find("上季样本")
                check(nodes().none { it.text?.toString()=="排期样本B" })
                click("上季样本"); check(selected.get()==3)
                click("回到当前"); find("排期样本A"); find("排期样本B")
            }
            if(seasonLive)click("上一季")
            Thread.sleep(if(live) 22000 else 200)
            val screenshot=test.uiAutomation.takeScreenshot()
            java.io.File(test.targetContext.getExternalFilesDir(null),if(seasonLive) "season-live.png" else if(live) "calendar-live.png" else "calendar-fixture.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }; screenshot.recycle()
        } finally {
            test.runOnMainSync { activity.finish() }; raw.edit().clear().commit(); settings.edit().clear().commit()
        }
    }
}
