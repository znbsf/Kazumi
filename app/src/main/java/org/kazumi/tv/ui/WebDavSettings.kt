@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package org.kazumi.tv.ui

import androidx.activity.compose.BackHandler
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
internal fun WebDavSettings(clientFactory:(WebDavAccount)->WebDavCollections={ WebDavCollections(it) }) {
    val context=LocalContext.current
    val credentials=remember { WebDavCredentialStore(context) }; val library=remember { LibraryStore(context) }
    var saved by remember { mutableStateOf(credentials.read()) }
    var directory by remember { mutableStateOf(saved?.directory.orEmpty()) }
    var username by remember { mutableStateOf(saved?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<CollectionSyncPreview?>(null) }
    var client by remember { mutableStateOf<WebDavCollections?>(null) }
    val scope=rememberCoroutineScope(); var job by remember { mutableStateOf<Job?>(null) }
    fun cancel() { job?.cancel(); preview=null; status="已取消；若提交已发出，远端可能已更新，请重新预览确认" }
    fun work(action:suspend()->Unit) {
        if(busy)return
        busy=true
        job=scope.launch {
            try { action() } catch(e:CancellationException) { throw e }
            catch(e:WebDavFailure) { preview=null; status=e.message.orEmpty() }
            catch(_:Exception) { preview=null; status="操作失败，请检查配置、数据版本或容量，并重新预览确认" }
            finally { busy=false }
        }
    }
    BackHandler(busy || preview!=null) { if(busy)cancel() else preview=null }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("WebDAV 收藏同步",style=KazumiType.heading)
        Text("同步收藏分类及删除记录。历史、来源规则和账号不包含在此文件中。",style=KazumiType.caption)
        if(status.isNotBlank())Text(status,style=KazumiType.body)
        if(busy) {
            Text("正在连接或提交…",style=KazumiType.body)
            PlayerAction("取消操作") { cancel() }
        } else if(preview!=null) {
            val value=preview!!
            Text("同步预览",style=KazumiType.title)
            Text("本机 ${value.local.collections.size} 条 · 远端 ${value.remote.snapshot.collections.size} 条 · 合并后 ${value.merged.collections.size} 条",style=KazumiType.body)
            Text("本机将新增 ${value.added} 条、修改 ${value.updated} 条、移除 ${value.removed} 条；上传 ${value.uploads} 项未同步变更。",style=KazumiType.body)
            Text("本机未同步操作优先应用；删除会同步到另一端。确认后更新远端和本机；中途失败请重新预览确认。",style=KazumiType.caption)
            value.merged.collections.take(4).forEach { Text("${it.type.label} · ${it.subject.title}",style=KazumiType.body) }
            PlayerAction("确认同步收藏") { val service=client!!; preview=null; work {
                withContext(Dispatchers.IO) { service.commit(library,value) }
                status="收藏同步完成"
            } }
            PlayerAction("取消预览") { preview=null }
        } else {
            Text("已存在的HTTPS目录",style=KazumiType.control)
            TvTextInput(directory,{ directory=it.take(2048) })
            Text("用户名",style=KazumiType.control); TvTextInput(username,{ username=it.take(512) })
            Text("密码（同一账号留空沿用已保存值）",style=KazumiType.control); TvTextInput(password,{ password=it.take(2048) },password=true)
            PlayerAction("保存WebDAV配置") { val old=saved
                val account=WebDavAccount(directory.trim(),username.trim(),password.ifEmpty { if(old?.directory==directory.trim() && old.username==username.trim())old.password else "" })
                work { withContext(Dispatchers.IO) { credentials.save(account) }; saved=account; password=""; status="配置已加密保存" }
            }
            if(saved!=null) {
                PlayerAction("连接并预览同步") { val account=saved!!; work {
                    val service=clientFactory(account)
                    val result=withContext(Dispatchers.IO) { service.preview(library) }
                    client=service; preview=result; status=""
                } }
                PlayerAction("移除本机WebDAV配置") { work { withContext(Dispatchers.IO) { credentials.clear() }; saved=null; password=""; directory=""; username=""; status="已移除本机配置，远端文件保留" } }
            }
            Text("连接使用已保存配置。文件名：kazumitv-collections-v1.json。服务器需支持强ETag及条件写入；本功能不读写原版Hive文件。",style=KazumiType.caption)
        }
    }
}
