@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package org.kazumi.tv.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import kotlinx.coroutines.*
import org.kazumi.tv.rules.*
import org.kazumi.tv.data.HttpText

@Composable
fun RulesScreen(readAddress: suspend (String) -> String = { address ->
    if(address.startsWith("kazumi:",true))address else HttpText.requestAsync(SourceRule.httpUrl(address),maxChars=RuleImport.MAX_CHARS)
}) {
    var online by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(online) { online = false }
    if (online) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { online = false }) { Text("返回已安装来源") }
            RuleCatalogScreen()
        }
        return
    }
    val context = LocalContext.current
    val store = remember { RuleStore(context) }
    var rules by remember { mutableStateOf(store.all()) }
    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<SourceRule?>(null) }
    var deleting by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(selected != null) { selected = null; deleting = false }
    val scope = rememberCoroutineScope()
    var importJob by remember { mutableStateOf<Job?>(null) }
    var importGeneration by remember { mutableIntStateOf(0) }
    fun cancelImport() {
        importGeneration++; importJob?.cancel(); importJob=null
        busy=false; status="已取消导入，现有来源保持不变"
    }
    androidx.activity.compose.BackHandler(busy) { cancelImport() }
    fun import(read: suspend () -> String) {
        if(busy)return
        busy=true
        val generation=++importGeneration
        importJob=scope.launch {
            try {
                val raw=withContext(Dispatchers.IO) { read() }
                ensureActive()
                if(generation==importGeneration) { status=store.importReport(raw).summary(); rules=store.all() }
            } catch(cancelled:CancellationException) { throw cancelled }
            catch(failure:Exception) { if(generation==importGeneration)status=failure.message ?: "导入失败" }
            finally { if(generation==importGeneration) { busy=false; importJob=null } }
        }
    }
    fun change(action: () -> Unit) {
        try { action(); rules = store.all(); status = "已保存"; selected = null; deleting = false }
        catch (failure: Exception) { status = failure.message ?: "操作失败" }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) import { context.contentResolver.openInputStream(uri)!!.bufferedReader().use { reader ->
            val buffer = CharArray(262145); var total = 0
            while (total < buffer.size) { val count = reader.read(buffer, total, buffer.size - total); if (count < 0) break; total += count }
            require(total <= 262144) { "规则文件超过大小限制" }; String(buffer, 0, total)
        } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("规则管理", style = MaterialTheme.typography.headlineMedium)
        if (selected != null) {
            val rule = selected!!
            Text("${rule.name} · ${rule.json.optString("version")}")
            Text(runCatching { rule.validateImport(); "格式与兼容检查通过；搜索、章节和播放尚需分别验证" }.getOrElse { it.message ?: "规则不兼容" })
            Text(status)
            Button(onClick = { selected = null; deleting = false }) { Text("返回规则列表") }
            Button(onClick = { change { store.toggle(rule) } }) { Text(if (store.isEnabled(rule)) "停用" else "启用") }
            Button(onClick = { change { store.move(rule, -1) } }) { Text("上移") }
            Button(onClick = { change { store.move(rule, 1) } }) { Text("下移") }
            Button(onClick = { change { store.rollback(rule) } }) { Text("恢复上一版本") }
            Button(onClick = { if (deleting) change { store.delete(rule) } else deleting = true }) { Text(if (deleting) "确认删除 ${rule.name}" else "删除规则") }
            return@Column
        }
        Button(enabled = !busy,onClick = { online = true }) { Text("在线规则目录 / 检查更新") }
        Text("导入原项目 JSON 规则；同名规则更新，关闭后不参与来源搜索。")
        BasicTextField(url, { url = it.take(RuleImport.MAX_CHARS) }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            modifier = Modifier.fillMaxWidth().background(Color(0xFF26342A)).padding(12.dp), decorationBox = { inner -> if (url.isBlank()) Text("输入规则网址或 kazumi:// 分享链接"); inner() })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = !busy && url.isNotBlank(), onClick = { val address=url.trim(); import { readAddress(address) } }) { Text("导入地址 / 分享") }
            Button(enabled = !busy, onClick = { try { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                catch (_: android.content.ActivityNotFoundException) { status = "此电视没有文件选择器，请使用网址导入" } }) { Text("从文件导入") }
        }
        if(busy)Button(onClick={ cancelImport() }) { Text("取消导入") }
        Text(if (busy) "正在导入…" else status)
        store.warning()?.let { Text(it) }
        Button(enabled = !busy,onClick = { change { store.restoreBuiltIns() } }) { Text("恢复被删除的内置来源") }
        LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(rules, key = { it.name }) { rule ->
                Button(enabled = !busy,onClick = { selected = rule; status = "" }) { Text("${if (store.isEnabled(rule)) "已启用" else "已关闭"} · ${rule.name} · ${rule.json.optString("version")}") }
            }
        }
    }
}
