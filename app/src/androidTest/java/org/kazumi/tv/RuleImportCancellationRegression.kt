package org.kazumi.tv
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.kazumi.tv.rules.*
import org.kazumi.tv.ui.*
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger

object RuleImportCancellationRegression {
    fun run(test:Instrumentation) {
        val real=test.targetContext.getSharedPreferences("tv_rules",0); val before=HashMap(real.all)
        val context=object:ContextWrapper(test.targetContext) { override fun getSharedPreferences(name:String,mode:Int)=baseContext.getSharedPreferences("import_cancel_$name",0) }
        val prefs=context.getSharedPreferences("tv_rules",0); prefs.edit().clear().commit()
        val raw=org.json.JSONObject(context.assets.open("rules/7sefun.json").bufferedReader().use { it.readText() }).put("name","导入取消测试").toString()
        val server=ServerSocket(0); val closed=AtomicInteger(); val received=AtomicInteger()
        val failure=java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val worker=Thread {
            try {
                repeat(3) { index -> server.accept().use { socket ->
                    socket.soTimeout=7000
                    val input=socket.getInputStream().bufferedReader()
                    while(true) { val line=input.readLine() ?: error("headers EOF"); if(line.isEmpty())break }
                    received.incrementAndGet()
                    if(index<2) { check(input.read()==-1); closed.incrementAndGet() }
                    else {
                        val body=raw.toByteArray(Charsets.UTF_8)
                        socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray()+body)
                        socket.getOutputStream().flush()
                    }
                } }
            } catch(e:Throwable) { failure.set(e) }
        }.apply { isDaemon=true; start() }
        val activity=test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            fun nodes():List<AccessibilityNodeInfo> {
                val result=mutableListOf<AccessibilityNodeInfo>()
                fun visit(n:AccessibilityNodeInfo?) { if(n==null)return; result.add(n); for(i in 0 until n.childCount)visit(n.getChild(i)) }
                visit(test.uiAutomation.rootInActiveWindow); return result
            }
            fun find(text:String):AccessibilityNodeInfo {
                repeat(80) { nodes().firstOrNull { it.text?.toString()==text }?.let { return it }; Thread.sleep(100) }
                error("Missing $text")
            }
            fun click(text:String) { var n:AccessibilityNodeInfo?=find(text); while(n!=null && !n.isClickable)n=n.parent; check(n?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true); Thread.sleep(350) }
            test.runOnMainSync { activity.setContent { CompositionLocalProvider(LocalContext provides context, androidx.activity.compose.LocalActivityResultRegistryOwner provides activity) { KazumiTheme(false) {
                Box(Modifier.fillMaxSize().background(KazumiColors.background).padding(30.dp)) { RulesScreen() }
            } } } }
            find("规则管理")
            val input=nodes().first { it.className?.toString()=="android.widget.EditText" }
            check(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"http://127.0.0.1:${server.localPort}/rule.json") }))
            repeat(2) { index ->
                click("导入地址 / 分享"); find("取消导入")
                repeat(30) { if(received.get()>index)return@repeat; Thread.sleep(50) }
                if(index==0)click("取消导入") else test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                find("已取消导入，现有来源保持不变")
                check(prefs.getString("imported",null)==null)
            }
            click("导入地址 / 分享")
            find("已导入 1 条，失败 0 条；尚未验证搜索和播放")
            worker.join(2000)
            check(failure.get()==null) { "Local server failed: ${failure.get()?.javaClass?.simpleName}" }
            check(closed.get()==2 && received.get()==3)
            check(RuleStore(context).all().count { it.name=="导入取消测试" }==1)
            check(HashMap(real.all)==before) { "Actual rules changed" }
        } finally { server.close(); test.runOnMainSync { activity.finish() }; prefs.edit().clear().commit() }
    }
}
