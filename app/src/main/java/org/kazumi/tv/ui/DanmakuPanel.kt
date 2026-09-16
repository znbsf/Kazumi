@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package org.kazumi.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.kazumi.tv.data.*

@Composable
fun DanmakuPanel(repository: DanmakuRepository?, title: String, selected: String, enabled: Boolean, offset: Long,
                 focus: FocusRequester, modifier: Modifier = Modifier, onClose: () -> Unit,
                 onToggle: () -> Unit, onOffset: (Long) -> Unit, onLoaded: (DanmakuEpisode, List<DanmakuComment>) -> Unit, onAutomatic: (() -> Unit)? = null) {
    var query by remember { mutableStateOf(title) }
    var submitted by remember { mutableStateOf(title) }
    var attempt by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf(emptyList<DanmakuEpisode>()) }
    var status by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(repository, submitted, attempt) {
        if (repository == null) return@LaunchedEffect
        loading = true; status = "正在搜索弹幕剧集…"; results = emptyList()
        try {
            results = repository.search(submitted)
            status = if (results.isEmpty()) "没有结果，请修改名称重试" else "请确认番剧、季度和集数（最多显示 500 集）"
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { status = danmakuError(error) }
        finally { loading = false }
    }
    Column(modifier.background(KazumiColors.background).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(modifier = Modifier.focusRequester(focus), onClick = onClose) { Text("返回播放") }
            Button(onClick = onToggle) { Text(if (enabled) "弹幕：开启" else "弹幕：关闭") }
        }
        Text("弹弹play开放弹幕网络", style = KazumiType.title)
        if (repository == null) {
            Text("请先到电视设置保存 AppId 和 AppSecret，再进入播放。")
        } else {
            if(onAutomatic!=null)PlayerAction("恢复自动匹配") { onAutomatic(); onClose() }
            if (selected.isNotBlank()) Text(selected, maxLines = 2, style = KazumiType.caption)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOffset((offset - 1000).coerceAtLeast(-120000)) }) { Text("提前 1 秒") }
                Button(onClick = { onOffset((offset + 1000).coerceAtMost(120000)) }) { Text("延后 1 秒") }
                Button(onClick = { onOffset(0) }) { Text("重置") }
            }
            Text("弹幕偏移：${offset / 1000} 秒（正数为延后）", style = KazumiType.caption)
            TvTextInput(query, { query = it.take(150) })
            Button(enabled = !loading, onClick = { submitted = query; attempt++ }) { Text("搜索剧集") }
            Text(status, style = KazumiType.caption)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(results, key = { it.id }) { episode ->
                    Button(enabled = !loading, modifier = Modifier.fillMaxWidth(), onClick = {
                        loading = true; status = "正在下载本集弹幕…"
                        scope.launch {
                            try {
                                val comments = repository.comments(episode.id)
                                if (comments.isEmpty()) status = "这集暂无弹幕，可选择其他剧集"
                                else { onLoaded(episode, comments); onClose() }
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (error: Exception) { status = danmakuError(error) }
                            finally { loading = false }
                        }
                    }) { Text(episode.title, maxLines = 2) }
                }
            }
        }
    }
}

private fun danmakuError(error: Exception): String = when(error) {
    is IllegalStateException, is IllegalArgumentException -> error.message ?: "弹幕加载失败"
    else -> "弹幕连接失败，请稍后重试（${error.javaClass.simpleName}）"
}
