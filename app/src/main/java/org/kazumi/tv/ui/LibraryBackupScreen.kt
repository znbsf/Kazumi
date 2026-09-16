@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package org.kazumi.tv.ui
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.*
import org.kazumi.tv.data.*

@Composable
internal fun LibraryBackupScreen() {
    val context=LocalContext.current
    val store=remember { LibraryStore(context) }; val files=remember { LocalLibraryBackups(context) }
    var saved by remember { mutableStateOf(files.list()) }
    var status by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<String?>(null) }; var fingerprint by remember { mutableStateOf("") }
    var exportRaw by remember { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    fun work(action:suspend()->Unit) {
        if(busy)return
        busy=true
        scope.launch { try { action() } catch(e:CancellationException) { throw e } catch(e:Exception) { status=e.message ?: "备份操作失败" } finally { busy=false } }
    }
    fun preview(raw:String) { LibraryArchiveCodec.read(raw); pending=raw; fingerprint=store.backupFingerprint(); status="" }
    BackHandler(pending!=null || busy) { if(!busy)pending=null }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null)work {
        val raw=withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { BoundedText.read(it,LibraryArchiveCodec.MAX_CHARS) } ?: error("无法读取文件") }
        preview(raw)
    } }
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val raw=exportRaw; exportRaw=null
        if(uri!=null)work {
            check(raw!=null) { "导出内容已失效，请重新导出" }
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri,"wt")?.bufferedWriter()?.use { it.write(raw) } ?: error("无法写入文件") }
            status="已导出收藏与历史备份"
        }
    }
    val archive=pending?.let { LibraryArchiveCodec.read(it) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("收藏与历史备份",style=KazumiType.heading)
        Text("包含收藏分类、观看进度和续播来源信息。来源规则及账号配置需另行保存。",style=KazumiType.caption)
        if(status.isNotBlank())Text(status,style=KazumiType.body)
        if(busy)Text("正在处理…",style=KazumiType.caption)
        if(archive!=null) {
            Text("恢复预览：${archive.collections.size} 条收藏，${archive.history.size} 条历史",style=KazumiType.title)
            Text("确认后将替换当前收藏与历史；已保留的内部旧版本不会被删除。恢复后可撤销一次，产生新记录后将禁止撤销覆盖。",style=KazumiType.body)
            archive.collections.take(3).forEach { Text("${it.type.label} · ${it.subject.title}",style=KazumiType.body) }
            archive.history.take(3).forEach { Text("${it.subject.title} · ${it.episode}",style=KazumiType.body) }
            if(!busy) {
                PlayerAction("确认替换收藏与历史") { val raw=pending!!; val expected=fingerprint; work { withContext(Dispatchers.IO) { store.restoreBackup(raw,expected) }; pending=null; status="恢复完成，可撤销上次恢复" } }
                PlayerAction("取消恢复") { pending=null }
            }
        } else if(!busy) {
            PlayerAction("保存本机备份") { work { withContext(Dispatchers.IO) { files.save(store.exportBackup()) }; saved=files.list(); status="本机备份已保存" } }
            Text("本机保留最近5份备份，继续保存会删除最旧的一份。卸载应用会删除本机备份，请导出重要副本。",style=KazumiType.caption)
            PlayerAction("导出到文件") { work {
                exportRaw=withContext(Dispatchers.IO) { store.exportBackup() }
                try { exporter.launch("KazumiTV-library-${System.currentTimeMillis()}.json") }
                catch(_:android.content.ActivityNotFoundException) { exportRaw=null; status="此电视没有文件保存器，可先保存本机备份" }
            } }
            PlayerAction("从文件恢复") { try { importer.launch(arrayOf("application/json","text/plain","application/octet-stream")) } catch(_:android.content.ActivityNotFoundException) { status="此电视没有文件选择器，可使用下方本机备份" } }
            if(store.canUndoRestore())PlayerAction("撤销上次恢复") { work { withContext(Dispatchers.IO) { store.undoRestore() }; status="已撤销恢复" } }
            Text("本机备份",style=KazumiType.title)
            if(saved.isEmpty())Text("暂无本机备份",style=KazumiType.caption)
            saved.forEachIndexed { index,file ->
                val date=java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",java.util.Locale.CHINA).format(java.util.Date(file.name.substringBefore('-').toLong()))
                PlayerAction("预览备份 ${index+1} · $date") { work { preview(withContext(Dispatchers.IO) { files.read(file) }) } }
            }
        }
    }
}
