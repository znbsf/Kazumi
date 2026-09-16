package org.kazumi.tv

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import kotlinx.coroutines.runBlocking
import org.kazumi.tv.rules.*
import org.kazumi.tv.ui.*

object VerificationLiveRegression {
    fun run(test:Instrumentation)=runBlocking {
        val repo=RuleRepository(test.targetContext)
        val rule=repo.rules.first { it.name.equals("mutefun",true) }
        val challenge=try {
            val matches=repo.search(rule,"无职转生")
            test.sendStatus(0,Bundle().apply { putString("stream","mutefun already verified results=${matches.size}\n") })
            return@runBlocking
        } catch(e:SourceVerificationRequired) { e }
        val activity=test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val done=java.util.concurrent.atomic.AtomicBoolean()
        val inputFile=java.io.File(test.targetContext.getExternalFilesDir(null),"verification-fixture-input.txt")
        inputFile.delete()
        test.runOnMainSync { activity.setContent { KazumiTheme(false) { VerificationScreen(rule,challenge.pageUrl,challenge) { done.set(true) } } } }
        test.sendStatus(0,Bundle().apply { putString("stream","mutefun verification UI open; awaiting manual image code, no OCR service used\n") })
        try {
            val deadline=System.currentTimeMillis()+300000
            while(!done.get()&&System.currentTimeMillis()<deadline) {
                if(inputFile.exists()) {
                    val code=inputFile.readText().trim();inputFile.delete()
                    val nodes=mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
                    fun walk(node:android.view.accessibility.AccessibilityNodeInfo?) { if(node==null)return;nodes.add(node);for(i in 0 until node.childCount)walk(node.getChild(i)) }
                    walk(test.uiAutomation.rootInActiveWindow)
                    check(nodes.any { it.text?.contains("mutefun · 网页验证")==true }) { "verification window is not active" }
                    check(nodes.first { it.isEditable }.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply { putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,code) }))
                    Thread.sleep(300)
                    nodes.clear();walk(test.uiAutomation.rootInActiveWindow)
                    var button=nodes.first { it.text?.toString()=="提交验证码" }
                    while(!button.isClickable)button=button.parent
                    check(button.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
                }
                Thread.sleep(200)
            }
            check(done.get()) { "manual verification not completed" }
            val matches=repo.search(rule,"无职转生")
            check(matches.isNotEmpty()) { "verification did not restore original search" }
            test.sendStatus(0,Bundle().apply { putString("stream","mutefun real image verification and original search retry results=${matches.size}=OK\n") })
        } finally { inputFile.delete();test.runOnMainSync { activity.finish() } }
    }
}
