@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package org.kazumi.tv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import androidx.media3.exoplayer.offline.Download
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.kazumi.tv.download.*
import org.kazumi.tv.playback.PlaybackRequest

@Composable
internal fun OfflinePlayback(id:String,onClose:()->Unit) {
    val context=LocalContext.current
    var task by remember(id) { mutableStateOf<Download?>(null) }
    var loaded by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) { task=withContext(Dispatchers.IO) { runCatching { OfflineDownloads.get(context).all().firstOrNull { it.request.id==id && it.state==Download.STATE_COMPLETED } }.getOrNull() }; loaded=true }
    val metadata=remember(task) { task?.let { runCatching { DownloadMetadata.read(it.request.data) }.getOrNull() } }
    if(!loaded)Text("正在读取离线内容…")
    else if(task==null || metadata==null) { Column { Text("下载未完成或记录不可用，请返回离线下载检查。") ; PlayerAction("返回",onClick=onClose) } }
    else PlayerScreen(PlaybackRequest(task!!.request.uri.toString(),emptyMap(),metadata.title,"offline|$id",task!!.request.mimeType,id),metadata.subject,onClose=onClose)
}

@Composable
internal fun DownloadsScreen() {
    val context=LocalContext.current
    val repository=remember { OfflineDownloads.get(context) }
    var tasks by remember { mutableStateOf<List<Download>>(emptyList()) }
    var problem by remember { mutableStateOf("") }
    var recovering by remember { mutableStateOf<Download?>(null) }
    var replacementId by remember { mutableStateOf<String?>(null) }
    var replacementError by remember { mutableStateOf("") }
    fun command(action:()->Unit) { runCatching(action).onFailure { problem=it.message ?: "下载操作失败" } }
    var selected by remember { mutableStateOf<String?>(null) }
    var restarting by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(repository) {
        while(true) {
            runCatching { withContext(Dispatchers.IO) { repository.all() } }.onSuccess { tasks=it }.onFailure { problem="读取下载记录失败" }
            replacementId=repository.replacingId(); replacementError=repository.replacementError
            delay(1000)
        }
    }
    if(selected!=null)Dialog(onDismissRequest={selected=null},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) { OfflinePlayback(selected!!) { selected=null } }
    if(recovering!=null)Dialog(onDismissRequest={recovering=null},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) { DownloadRecoveryPanel(recovering!!) { recovering=null } }
    LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("离线下载",style=KazumiType.heading) }
        item { Text("仅非计费网络 · 同时下载1集 · 总量上限4 GiB · 保留256 MiB可用空间",style=KazumiType.caption) }
        item { Text("支持普通 MP4、已结束的 HLS 和静态 DASH；暂不支持直播及 DRM 授权下载。HLS/DASH 当前下载全部轨道，外部字幕与弹幕不缓存。重启后选择“恢复待下载任务”继续。",style=KazumiType.caption) }
        item { PlayerAction("恢复待下载任务") { androidx.media3.exoplayer.offline.DownloadService.start(context,TvDownloadService::class.java) } }
        if(replacementError.isNotBlank())item { Text(replacementError,style=KazumiType.body) }
        if(problem.isNotBlank())item { Text(problem,style=KazumiType.body) }
        if(tasks.isEmpty())item { Text("暂无下载。播放时选择“下载本集”加入任务。",style=KazumiType.body) }
        items(tasks,key={it.request.id}) { task ->
            val metadata=remember(task.request) { runCatching { DownloadMetadata.read(task.request.data) }.getOrNull() }
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(metadata?.title ?: "无法读取任务信息",style=KazumiType.title)
                val replacing=task.request.id==replacementId
                val state=if(replacing)"正在替换下载地址" else when(task.state) { Download.STATE_COMPLETED -> "已完成"; Download.STATE_DOWNLOADING -> "下载中"; Download.STATE_STOPPED -> "已暂停"; Download.STATE_FAILED -> repository.failureReason(task.request.id); Download.STATE_REMOVING -> "正在删除"; else -> "等待非计费网络或前序任务" }
                Text("$state · ${String.format(java.util.Locale.ROOT,"%.1f",task.bytesDownloaded/1048576.0)} MiB"+(if(task.percentDownloaded>=0)" · ${task.percentDownloaded.toInt()}%" else ""),style=KazumiType.caption)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(!replacing) {
                    if(task.state==Download.STATE_COMPLETED && metadata!=null)PlayerAction("离线播放") { selected=task.request.id }
                    else if(task.state==Download.STATE_STOPPED || task.state==Download.STATE_FAILED)PlayerAction("继续 / 重试") { command { repository.resume(task.request.id) } }
                    else if(task.state!=Download.STATE_REMOVING)PlayerAction("暂停") { command { repository.pause(task.request.id) } }
                    if(task.state==Download.STATE_STOPPED || task.state==Download.STATE_FAILED)PlayerAction("从头重新下载") { restarting=task.request.id }
                    if(metadata?.origin!=null && task.state in listOf(Download.STATE_STOPPED,Download.STATE_FAILED))PlayerAction("重新解析地址") { recovering=task }
                    }
                    if(task.state!=Download.STATE_REMOVING)PlayerAction("删除下载") { deleting=task.request.id }
                }
                if(restarting==task.request.id)Row {
                    PlayerAction("清除本集片段并重下") { command { repository.restart(task.request.id) }; restarting=null }
                    PlayerAction("取消重下") { restarting=null }
                }
                if(deleting==task.request.id)Row {
                    PlayerAction("确认删除本集文件") { command { repository.remove(task.request.id) }; deleting=null }
                    PlayerAction("取消") { deleting=null }
                }
            }
        }
    }
}
