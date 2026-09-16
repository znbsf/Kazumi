@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package org.kazumi.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kazumi.tv.data.*

@Composable
fun DanmakuSettings() {
    val context = LocalContext.current
    val store = remember { DanmakuCredentialStore(context) }
    val scope = rememberCoroutineScope()
    var appId by remember { mutableStateOf(store.read()?.appId.orEmpty()) }
    var secret by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(if (store.read() == null) "尚未配置弹幕凭证" else "已保存凭证，密钥不回显") }
    var busy by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("弹幕服务", style = KazumiType.heading)
        Text("弹弹play开放弹幕网络 · www.dandanplay.com", style = KazumiType.caption)
        Text("AppId", style = KazumiType.control)
        TvTextInput(appId, { appId = it.take(128) })
        Text("AppSecret（已保存时留空可沿用）", style = KazumiType.control)
        TvTextInput(secret, { secret = it.take(512) }, password = true)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        val previous = store.read()
                        check(secret.isNotBlank() || previous?.appId == appId.trim()) { "更换 AppId 时也需要填写密钥" }
                        withContext(Dispatchers.IO) { store.save(DanmakuCredentials(appId.trim(), secret.trim().ifBlank { previous?.secret.orEmpty() })) }
                        secret = ""; status = "凭证已加密保存在这台电视，可在播放器中搜索并选择弹幕剧集"
                    } catch (_: Exception) { status = "保存失败，请检查 AppId、密钥与设备加密支持" }
                    finally { busy = false }
                }
            }) { Text("保存凭证") }
            Button(enabled = !busy, onClick = { store.clear(); appId = ""; secret = ""; status = "已移除本机凭证" }) { Text("移除凭证") }
        }
        Text(status)
        Text("播放时选择“弹幕”，确认番剧和集数后加载；支持开关和时间校准。", style = KazumiType.caption)
    }
}

@Composable
fun TvTextInput(value: String, onChange: (String) -> Unit, password: Boolean = false, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    BasicTextField(value, onChange, singleLine = true,
        textStyle = KazumiType.body.copy(color = KazumiColors.text),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if (password) androidx.compose.ui.text.input.KeyboardType.Password else androidx.compose.ui.text.input.KeyboardType.Text),
        modifier = modifier.fillMaxWidth().onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            val direction = when(native.keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_UP -> FocusDirection.Up
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> FocusDirection.Down
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> FocusDirection.Right
                else -> null
            }
            if (native.action == android.view.KeyEvent.ACTION_DOWN && direction != null) focus.moveFocus(direction) else false
        }.background(KazumiColors.surface).padding(12.dp))
}
