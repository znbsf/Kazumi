package org.kazumi.tv
import android.app.Instrumentation
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.kazumi.tv.playback.*
import org.kazumi.tv.ui.*

object CapabilityRegression {
    fun run(test:Instrumentation) {
        val prefs=test.targetContext.getSharedPreferences("tv_settings",0).all.toMap()
        val library=test.targetContext.getSharedPreferences("tv_library",0).all.toMap()
        val activity=test.startActivitySync(Intent(test.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            test.runOnMainSync {
                val actual=DeviceCapabilities.read(activity,activity.window.decorView)
                check(actual.displayLabel!=null && actual.hdr!=null && actual.outputs!=null)
                android.util.Log.i("CapabilityTest","display=${actual.displayLabel}; HDR=${actual.hdrLabel}; outputs=${actual.outputs}")
                check(DeviceCapabilities(null,null,null).hdrLabel.contains("无法读取"))
                check(DeviceCapabilities(emptyList(),emptyList(),null).hdrLabel.contains("未报告"))
                check(DeviceCapabilities(listOf(2,999),null,null).hdrLabel=="HDR10 / HDR 类型 999")
                check(AudioOutputCapability(0,999,emptyList(),emptyList(),emptyList()).channelLabel.contains("未限定"))
                activity.setContent { KazumiTheme(false) { androidx.compose.runtime.CompositionLocalProvider(androidx.tv.material3.LocalContentColor provides Color(0xffe4eee1)) { Column(Modifier.fillMaxSize().background(Color(0xff101412)).padding(28.dp)) { DeviceCapabilitiesPanel() } } } }
            }
            test.waitForIdleSync(); Thread.sleep(1200)
            fun text():String {
                fun walk(node:android.view.accessibility.AccessibilityNodeInfo?):String {
                    if(node==null)return ""
                    return node.text.toString()+" "+(0 until node.childCount).joinToString(" ") { walk(node.getChild(it)) }
                }
                return walk(test.uiAutomation.rootInActiveWindow)
            }
            check(text().contains("设备与音画能力") && text().contains("重新检测"))
            // D-pad navigation and repeated entry exercise the production listener lifecycle.
            test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
            test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
            test.waitForIdleSync()
            test.uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(test.targetContext.getExternalFilesDir(null),"device-capabilities.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
            } ?: error("screenshot unavailable")
            repeat(3) {
                test.runOnMainSync { activity.setContent { } }; test.waitForIdleSync()
                test.runOnMainSync { activity.setContent { DeviceCapabilitiesPanel() } }; test.waitForIdleSync()
            }
            check(test.targetContext.getSharedPreferences("tv_settings",0).all==prefs)
            check(test.targetContext.getSharedPreferences("tv_library",0).all==library)
        } finally { test.runOnMainSync { activity.finish() } }
    }
}
