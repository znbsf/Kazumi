@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package org.kazumi.tv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.kazumi.tv.data.NetworkSettings
import org.kazumi.tv.rules.*

@Composable
fun RuleCatalogScreen(load: suspend () -> List<CatalogRule> = { RuleCatalog().list() },
                      install: suspend (CatalogRule,RuleStore) -> Int = { entry,store -> RuleCatalog().install(entry,store) }) {
    val context = LocalContext.current
    val store = remember { RuleStore(context) }
    val catalog = remember { RuleCatalog() }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(emptyList<CatalogRule>()) }
    var installed by remember { mutableStateOf(store.all()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        busy = true; status = "正在读取规则目录…"
        try { entries = load(); status = "共 ${entries.size} 个来源；安装前仍需确认规则兼容性" }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { status = "规则目录连接失败，可切换镜像后重试；已安装的本地规则会保留" }
        finally { busy = false }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = !busy, onClick = { reload++ }) { Text("刷新目录") }
            Button(enabled = !busy, onClick = { NetworkSettings.rulesMirror = !NetworkSettings.rulesMirror; reload++ }) { Text("切换镜像并重试") }
        }
        Text("当前：${if (NetworkSettings.rulesMirror) "GitCode 镜像" else "GitHub"} · $status", style = KazumiType.caption)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(entries, key = { it.name }) { entry ->
                val existing = installed.firstOrNull { it.name.equals(entry.name, true) }
                val same = existing?.json?.optString("version") == entry.version
                Button(enabled = !busy && !same, modifier = Modifier.fillMaxWidth(), onClick = {
                    busy = true
                    scope.launch {
                        try { install(entry, store); installed = store.all(); status = "${entry.name} 已${if (existing == null) "安装" else "更新"}" }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { status = if (error is IllegalArgumentException) error.message.orEmpty() else "${entry.name} 安装失败，可稍后重试" }
                        finally { busy = false }
                    }
                }) { Text("${entry.name}  ${entry.version}   ·   ${if (same) "已安装" else if (existing == null) "添加" else "更新本机 ${existing.json.optString("version")}"}") }
            }
        }
    }
}
