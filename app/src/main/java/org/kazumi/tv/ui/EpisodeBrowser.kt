@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package org.kazumi.tv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import androidx.compose.ui.graphics.Color
import org.kazumi.tv.domain.EpisodeWindow

/** Display ordering never changes the original index supplied to playback. */
@Composable
internal fun EpisodeBrowser(titles: List<String>,current: Int=-1,seen: Set<Int> = emptySet(),
                            modifier: Modifier=Modifier,restoreIndex: Int?=null,compact:Boolean=false,onSelect:(Int)->Unit) {
    var descending by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableIntStateOf(EpisodeWindow.pageOf(current,titles.size,false)) }
    var number by rememberSaveable { mutableStateOf("") }
    var locating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var focusTarget by remember { mutableStateOf<Int?>(null) }
    val requesters=remember { mutableMapOf<Int,FocusRequester>() }
    val grid=rememberLazyGridState()
    val indices=remember(titles.size,page,descending) { EpisodeWindow.indices(titles.size,page,descending) }
    fun locate(index:Int) { page=EpisodeWindow.pageOf(index,titles.size,descending); focusTarget=index }
    LaunchedEffect(restoreIndex) { restoreIndex?.takeIf { it in titles.indices }?.let { locate(it) } }
    LaunchedEffect(page,descending) { if(focusTarget==null)grid.scrollToItem(0) }
    LaunchedEffect(focusTarget,page,descending) {
        focusTarget?.let { index ->
            val visible=indices.indexOf(index)
            if(visible>=0) { grid.scrollToItem(visible); withFrameNanos { }; withFrameNanos { }; requesters[index]?.requestFocus(); focusTarget=null }
        }
    }
    val countLabel="共 ${titles.size} 项 · ${if(indices.isEmpty()) 0 else page+1}/${maxOf(1,(titles.size+49)/50)} 段"
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(!compact)Text("共 ${titles.size} 项 · ${if(indices.isEmpty()) 0 else page+1}/${maxOf(1,(titles.size+49)/50)} 段",style=KazumiType.caption)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            if(compact)Text(countLabel,style=KazumiType.caption,modifier=Modifier.weight(1f))
            PlayerAction(if(descending) "倒序" else "正序") {
                val anchor=indices.firstOrNull() ?: 0
                descending=!descending; page=EpisodeWindow.pageOf(anchor,titles.size,descending)
            }
            if(compact)PlayerAction("定位序号") { error=null; locating=true }
            if(current in titles.indices)PlayerAction("定位当前") { locate(current) }
            if(page>0)PlayerAction("上一段") { page--; focusTarget=null }
            if(page<(titles.size-1)/50)PlayerAction("下一段") { page++; focusTarget=null }
        }
        if(!compact)Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            TvTextInput(number,{ number=it.filter(Char::isDigit).take(6) },modifier=Modifier.weight(1f))
            PlayerAction("定位序号") {
                val index=EpisodeWindow.locate(number,titles.size)
                if(index==null)error="请输入1至${titles.size}的列表序号" else { error=null; locate(index) }
            }
        }
        if(!compact)Text(error ?: "序号按原始列表计算，包含特别篇；已看表示有观看进度。",style=KazumiType.caption)
        LazyVerticalGrid(columns=GridCells.Fixed(4),state=grid,modifier=Modifier.weight(1f),
            horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(4.dp)) {
            items(indices,key={it}) { index ->
                DisposableEffect(index) { onDispose { requesters.remove(index) } }
                Button(modifier=Modifier.fillMaxWidth().height(60.dp).focusRequester(requesters.getOrPut(index) { FocusRequester() }),
                    scale=ButtonDefaults.scale(focusedScale=1f),
                    colors=ButtonDefaults.colors(containerColor=Color.Transparent,focusedContainerColor=Color.White.copy(alpha=.12f),contentColor=KazumiColors.text,focusedContentColor=KazumiColors.accent),
                    onClick={ onSelect(index) }) {
                    Text("${index+1}. ${if(index==current) "当前 · " else if(index in seen) "已看 · " else ""}${titles[index]}",style=KazumiType.control,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
        }
    }
    if(locating)androidx.compose.ui.window.Dialog(onDismissRequest={ locating=false }) {
        val jumpFocus=remember { FocusRequester() }
        val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        DisposableEffect(keyboard) { onDispose { keyboard?.hide() } }
        LaunchedEffect(Unit) { withFrameNanos { }; jumpFocus.requestFocus() }
        Column(Modifier.width(480.dp).then(Modifier.background(KazumiColors.background)).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("定位选集",style=KazumiType.title)
            Text("输入1至${titles.size}的原始列表序号，包含特别篇。",style=KazumiType.caption)
            TvTextInput(number,{ number=it.filter(Char::isDigit).take(6) })
            error?.let { Text(it,style=KazumiType.caption) }
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                PlayerAction("跳转",Modifier.focusRequester(jumpFocus)) {
                    val index=EpisodeWindow.locate(number,titles.size)
                    if(index==null)error="请输入1至${titles.size}的列表序号" else { error=null; keyboard?.hide(); locating=false; locate(index) }
                }
                PlayerAction("取消定位") { keyboard?.hide(); locating=false }
            }
        }
    }

}
