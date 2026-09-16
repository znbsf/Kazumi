package org.kazumi.tv

import android.app.Instrumentation
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.*
import org.kazumi.tv.playback.NativePlayer
import org.kazumi.tv.playback.PlaybackRequest
import org.kazumi.tv.ui.*
import java.util.concurrent.atomic.AtomicInteger

/** Offline device regression: exercises retained AndroidView and cancellation without real sources or credentials. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object S0Regression {
    fun run(test: Instrumentation) {
        val activity = test.startActivitySync(Intent(test.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        lateinit var first: NativePlayer
        lateinit var second: NativePlayer
        val selected = mutableIntStateOf(0)
        val shown = mutableStateOf(true)
        var retained: PlayerView? = null
        fun find(view: View): PlayerView? {
            if (view is PlayerView) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        fun nodesContaining(text: String): List<android.view.accessibility.AccessibilityNodeInfo> {
            val found = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
            fun visit(node: android.view.accessibility.AccessibilityNodeInfo?) {
                if (node == null) return
                if (node.text?.contains(text) == true) found.add(node)
                for (i in 0 until node.childCount) visit(node.getChild(i))
            }
            visit(test.uiAutomation.rootInActiveWindow)
            return found
        }
        fun settle() { Thread.sleep(600); test.waitForIdleSync() }
        try {
            test.runOnMainSync {
                first = NativePlayer(activity); second = NativePlayer(activity)
                activity.setContent { if (shown.value) PlaybackVideoSurface(if (selected.intValue == 0) first.player else second.player, false) }
            }
            settle()
            test.runOnMainSync { retained = checkNotNull(find(activity.window.decorView)); check(retained!!.player === first.player); selected.intValue = 1 }
            settle()
            test.runOnMainSync {
                check(find(activity.window.decorView) === retained) { "View unexpectedly recreated: regression must exercise update" }
                check(retained!!.player === second.player) { "Retained view still bound to old engine" }
                shown.value = false
            }
            settle()
            test.runOnMainSync { check(retained!!.player == null) { "Removed view retained player" } }

            val calls = AtomicInteger()
            val delivered = AtomicInteger()
            val open = mutableStateOf(true)
            val selection = mutableStateOf("first")
            test.runOnMainSync { activity.setContent { KazumiTheme(false) {
                if (open.value) ResolvingPlayback(selection.value, "S0 解析回归", resolve = {
                    calls.incrementAndGet()
                    withContext(NonCancellable) { delay(2200) }
                    PlaybackRequest("https://example.invalid/unused.mp4", emptyMap(), "fixture")
                }, onClose = { open.value = false }) { SideEffect { delivered.incrementAndGet() } }
            } } }
            settle()
            check(calls.get() == 1)
            check(nodesContaining("正在解析播放地址").isNotEmpty())
            test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            Thread.sleep(2400)
            check(delivered.get() == 0) { "Cancelled result opened media" }

            test.runOnMainSync { activity.setContent { KazumiTheme(false) {
                ResolvingPlayback("retry", "S0 错误回归", resolve = {
                    calls.incrementAndGet(); delay(150); error("fixture")
                }, onClose = {}) { error("failed resolver delivered media") }
            } } }
            settle()
            check(nodesContaining("解析失败").isNotEmpty())
            val before = calls.get()
            val nodes = nodesContaining("重新解析")
            check(nodes.isNotEmpty())
            var node = nodes.first()
            while (!node.isClickable && node.parent != null) node = node.parent
            check(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            settle()
            check(calls.get() == before + 1) { "Retry did not restart resolver" }
        } finally {
            test.runOnMainSync {
                activity.setContent {}
                first.release(); second.release(); activity.finish()
            }
        }
    }
}
