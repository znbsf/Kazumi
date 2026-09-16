@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package org.kazumi.tv.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import org.kazumi.tv.data.*

@Composable
internal fun RelationsScreen(subject:Subject,load:suspend(Int)->RelationResult,onBack:()->Unit,onSelect:(Subject)->Unit) {
    var result by remember(subject.id) { mutableStateOf<RelationResult?>(null) }
    var failed by remember(subject.id) { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val focus=remember { mutableMapOf<Int,FocusRequester>() }
    val list=rememberLazyGridState()
    val backFocus=remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; backFocus.requestFocus() }
    val revision by NetworkSettings.catalogRevision.collectAsState()
    LaunchedEffect(subject.id,attempt,revision) {
        result=null; failed=false
        try { result=load(subject.id) } catch(e:CancellationException) { throw e } catch(_:Exception) { failed=true }
    }
    LaunchedEffect(result) { if(result!=null) { val index=result!!.items.indexOfFirst { it.subject.id==selected }; if(index>=0)list.scrollToItem(index); withFrameNanos { }; withFrameNanos { }; focus[selected]?.requestFocus() } }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("${subject.title} · 关联动画",style=KazumiType.heading)
        PlayerAction("返回作品详情",Modifier.focusRequester(backFocus),onClick=onBack)
        if(failed)PlayerAction("关联读取失败，重试") { attempt++ }
        else if(result==null)Text("正在读取前传、续集与关联作品…",style=KazumiType.body)
        result?.let { value ->
            if(value.truncated)Text("关联链较长，已显示部分结果；进入具体作品可继续浏览。",style=KazumiType.caption)
            if(value.items.isEmpty())Text("暂无关联动画",style=KazumiType.body)
            LazyVerticalGrid(columns=GridCells.Fixed(6),state=list,contentPadding=PaddingValues(6.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.weight(1f)) {
                items(value.items,key={it.subject.id}) { row ->
                    DisposableEffect(row.subject.id) { onDispose { focus.remove(row.subject.id) } }
                    Card(onClick={ selected=row.subject.id; onSelect(row.subject) },colors=CardDefaults.colors(containerColor=KazumiColors.surface,focusedContainerColor=KazumiColors.selected),scale=CardDefaults.scale(focusedScale=1.035f),modifier=Modifier.height(220.dp).focusRequester(focus.getOrPut(row.subject.id) { FocusRequester() })) {
                        Box(Modifier.fillMaxSize()) {
                            CoverImage(row.subject.cover,row.subject.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                            Box(Modifier.fillMaxWidth().height(90.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black))))
                            Text("${row.relation} · ${row.subject.title}",style=KazumiType.body,maxLines=3,overflow=TextOverflow.Ellipsis,color=Color.White,modifier=Modifier.align(Alignment.BottomStart).padding(10.dp))
                        }
                    }
                }
            }
        }
    }
}
