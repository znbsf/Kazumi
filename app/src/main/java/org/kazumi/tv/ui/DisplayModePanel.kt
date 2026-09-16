@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package org.kazumi.tv.ui

import android.view.View
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Text
import org.kazumi.tv.playback.DisplayModeSession

internal fun displayWindow(view:View,fallback:Window?):Window? {
    var parent=view.parent
    while(parent!=null) { if(parent is DialogWindowProvider)return parent.window; parent=parent.parent }
    return fallback
}
@Composable
internal fun rememberDisplayModeSession():DisplayModeSession? {
    val context=LocalContext.current; val view=LocalView.current; val activity=LocalActivity.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val window=displayWindow(view,activity?.window)
    val session=remember(context,window,view) { window?.let { DisplayModeSession(context,it,view) } }
    DisposableEffect(session,lifecycle) {
        val observer=LifecycleEventObserver { _,event -> when(event) {
            Lifecycle.Event.ON_START -> session?.start()
            Lifecycle.Event.ON_STOP -> session?.stop()
            else -> Unit
        } }
        lifecycle.addObserver(observer)
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))session?.start()
        onDispose { lifecycle.removeObserver(observer); session?.stop() }
    }
    return session
}

@Composable
internal fun DisplayModePanel(session:DisplayModeSession?=null,duringPlayback:Boolean=false) {
    val controller=session ?: rememberDisplayModeSession()
    if(controller==null) { Text("无法获取显示窗口",style=KazumiType.body); return }
    val state by controller.state.collectAsState()
    val first=remember { FocusRequester() }
    BackHandler(state.trial!=null) { controller.cancelPreview() }
    DisposableEffect(controller) { onDispose { controller.cancelPreview() } }
    LaunchedEffect(state.trial!=null) { withFrameNanos { }; first.requestFocus() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("显示模式",style=KazumiType.heading)
        Text("系统当前：${state.display.active?.preference?.label ?: "未知"}",style=KazumiType.body)
        Text("已保存选择：${state.saved?.label ?: "系统默认"}",style=KazumiType.caption)
        Text(if(duringPlayback)"选择用于本次及以后播放；退出播放器恢复此前显示请求。" else "本页可试用并保存；离开设置恢复此前显示请求，保存的选择在播放时应用。",style=KazumiType.caption)
        Text("系统默认不代表自动匹配视频帧率。系统可能忽略应用请求，请以当前模式为准。",style=KazumiType.caption)
        if(state.status.isNotBlank())Text(state.status,style=KazumiType.body)
        if(state.trial!=null) {
            Text("试用：${state.trial?.choice?.label ?: "系统默认"}",style=KazumiType.title)
            Text("请在 ${state.seconds} 秒内确认，否则恢复原选择。",style=KazumiType.body)
            PlayerAction("保留显示选择",Modifier.focusRequester(first)) { controller.confirm() }
            PlayerAction("恢复原选择") { controller.cancelPreview() }
        } else {
            val modes=state.display.modes.distinctBy { it.preference }
            if(modes.size==1)Text("此电视目前只公开一种显示模式。",style=KazumiType.caption)
            if(modes.isEmpty())Text("未能读取可用模式，可保留系统默认。",style=KazumiType.caption)
            PlayerAction("系统默认",Modifier.focusRequester(first)) { controller.preview(null) }
            modes.sortedWith(compareBy({it.preference.width},{it.preference.height},{it.preference.milliHz})).forEach { mode ->
                PlayerAction(mode.preference.label) { controller.preview(mode.preference) }
            }
        }
    }
}
