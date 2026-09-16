@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package org.kazumi.tv.ui

import android.hardware.display.DisplayManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Text
import org.kazumi.tv.playback.DeviceCapabilities

@Composable
internal fun DeviceCapabilitiesPanel() {
    val context=LocalContext.current; val view=LocalView.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var state by remember(context,view) { mutableStateOf(DeviceCapabilities.read(context,view)) }
    DisposableEffect(context,view,lifecycle) {
        var active=false
        val refresh={ if(active)state=DeviceCapabilities.read(context,view) }
        val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val display=context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val handler=Handler(Looper.getMainLooper())
        val audioCallback=object:AudioDeviceCallback() {
            override fun onAudioDevicesAdded(devices:Array<out AudioDeviceInfo>) { refresh() }
            override fun onAudioDevicesRemoved(devices:Array<out AudioDeviceInfo>) { refresh() }
        }
        val displayCallback=object:DisplayManager.DisplayListener {
            override fun onDisplayAdded(id:Int) { refresh() }
            override fun onDisplayRemoved(id:Int) { refresh() }
            override fun onDisplayChanged(id:Int) { refresh() }
        }
        fun start() {
            if(active)return
            active=true
            audio.registerAudioDeviceCallback(audioCallback,handler)
            display.registerDisplayListener(displayCallback,handler)
            refresh()
        }
        fun stop() {
            if(!active)return
            active=false
            audio.unregisterAudioDeviceCallback(audioCallback)
            display.unregisterDisplayListener(displayCallback)
        }
        val observer=LifecycleEventObserver { _,event ->
            if(event==Lifecycle.Event.ON_START)start()
            if(event==Lifecycle.Event.ON_STOP)stop()
        }
        lifecycle.addObserver(observer)
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))start()
        onDispose { lifecycle.removeObserver(observer); stop() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("设备与音画能力",style=KazumiType.heading)
        PlayerAction("重新检测") { state=DeviceCapabilities.read(context,view) }
        Text("当前显示：${state.displayLabel ?: "未知"}",style=KazumiType.body)
        Text(state.hdrLabel,style=KazumiType.body)
        Text("这是系统报告的能力，不代表当前视频已经以 HDR 输出。兼容输出、视频格式及电视设置也会影响实际效果。",style=KazumiType.caption)
        Text("可用音频输出",style=KazumiType.title)
        Text("以下列出可用设备，不代表当前音频路由。多声道及直通仍需匹配音源、接收设备与系统设置。",style=KazumiType.caption)
        when {
            state.outputs==null -> Text("无法读取音频设备",style=KazumiType.body)
            state.outputs!!.isEmpty() -> Text("系统未报告可用音频输出",style=KazumiType.body)
            else -> state.outputs!!.forEach { output ->
                Text("${output.label} · ${output.channelLabel}",style=KazumiType.body)
                Text(if(output.encodings.isEmpty())"系统未列出具体编码" else output.encodings.distinct().joinToString(" / ") { DeviceCapabilities.encodingName(it) },style=KazumiType.caption)
                Text(if(output.rates.isEmpty())"系统未限定采样率" else output.rates.distinct().sorted().joinToString(" / ")+" Hz",style=KazumiType.caption)
            }
        }
    }
}
