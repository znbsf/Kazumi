@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package org.kazumi.tv.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import kotlinx.coroutines.CancellationException
import org.kazumi.tv.data.*

@Composable
internal fun CalendarScreen(load:suspend(Boolean,AnimeSeason?)->WeekSchedule={ refresh,season -> CalendarRepository().load(refresh,season) },onSelect:(Subject)->Unit) {
    val context=LocalContext.current
    val preferences=remember { context.getSharedPreferences("tv_settings",0) }
    var day by rememberSaveable { mutableIntStateOf((java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)+5)%7) }
    var watching by rememberSaveable { mutableStateOf(preferences.getBoolean("schedule_watching",false)) }
    var hideWatched by rememberSaveable { mutableStateOf(preferences.getBoolean("schedule_hide_watched",false)) }
    var hideAbandoned by rememberSaveable { mutableStateOf(preferences.getBoolean("schedule_hide_abandoned",false)) }
    var seasonOffset by rememberSaveable { mutableIntStateOf(0) }
    val season=AnimeSeason.current().shift(seasonOffset)
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var schedule by remember { mutableStateOf<WeekSchedule?>(null) }
    var error by remember { mutableStateOf(false) }; var loading by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val revision by NetworkSettings.catalogRevision.collectAsState()
    val grid=rememberLazyGridState()
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val focus=remember { mutableMapOf<Int,FocusRequester>() }
    LaunchedEffect(retry,revision,seasonOffset) {
        loading=true; error=false; schedule=null
        try { schedule=load(retry>0,season.takeIf { seasonOffset!=0 }) } catch(cancelled:CancellationException) { throw cancelled } catch(_:Exception) { error=true }
        finally { loading=false }
    }
    val library=remember { LibraryStore(context) }
    val rows=ScheduleQuery.filter(schedule?.days?.getOrNull(day).orEmpty(),library.collections(),watching,hideWatched,hideAbandoned,sort)
    LaunchedEffect(schedule,selected) { selected?.let { id -> val index=rows.indexOfFirst { it.id==id }; if(index>=0) { grid.scrollToItem(index); withFrameNanos { }; withFrameNanos { }; focus[id]?.requestFocus() } } }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(if(seasonOffset==0) "每周排期" else season.label,style=KazumiType.heading)
            if(season.year>1900 || season.quarter>1)PlayerAction("上一季") { selected=null; seasonOffset-- }
            if(seasonOffset!=0)PlayerAction("回到当前") { selected=null; seasonOffset=0 }
            if(seasonOffset<4)PlayerAction("下一季") { selected=null; seasonOffset++ }
        }
        Text(if(seasonOffset==0) "排期来自节目资料，不代表播放来源已经更新。" else "季度资料按首播日期归入星期，不代表每周更新日。",style=KazumiType.caption)
        LazyRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            items((0..6).toList()) { index -> PlayerAction((if(index==day) "✓ " else "")+listOf("周一","周二","周三","周四","周五","周六","周日")[index]) { day=index; selected=null } }
        }
        LazyRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            item { PlayerAction("只看在追：${if(watching) "开" else "关"}") { watching=!watching; preferences.edit().putBoolean("schedule_watching",watching).apply() } }
            item { PlayerAction("隐藏看过：${if(hideWatched) "开" else "关"}") { hideWatched=!hideWatched; preferences.edit().putBoolean("schedule_hide_watched",hideWatched).apply() } }
            item { PlayerAction("隐藏抛弃：${if(hideAbandoned) "开" else "关"}") { hideAbandoned=!hideAbandoned; preferences.edit().putBoolean("schedule_hide_abandoned",hideAbandoned).apply() } }
            item { PlayerAction("排序：${listOf("人气","评分","默认")[sort]}") { sort=(sort+1)%3 } }
            item { PlayerAction("刷新排期") { retry++ } }
        }
        schedule?.notice?.let { Text(it,style=KazumiType.caption) }
        if(loading)Text("正在读取排期…",style=KazumiType.caption)
        if(error)PlayerAction("排期读取失败，重试") { retry++ }
        if(!loading && !error && rows.isEmpty())Text("当天没有符合条件的节目",style=KazumiType.body)
        LazyVerticalGrid(columns=GridCells.Fixed(6),state=grid,modifier=Modifier.weight(1f),contentPadding=PaddingValues(6.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            items(rows,key={it.id}) { subject ->
                DisposableEffect(subject.id) { onDispose { focus.remove(subject.id) } }
                Card(onClick={ selected=subject.id; onSelect(subject) },colors=CardDefaults.colors(containerColor=KazumiColors.surface,focusedContainerColor=KazumiColors.selected),scale=CardDefaults.scale(focusedScale=1.035f),modifier=Modifier.height(200.dp).focusRequester(focus.getOrPut(subject.id) { FocusRequester() })) {
                    Box(Modifier.fillMaxSize()) {
                        CoverImage(subject.cover,subject.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                        Box(Modifier.fillMaxWidth().height(75.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black))))
                        Text(subject.title,style=KazumiType.body,maxLines=2,overflow=TextOverflow.Ellipsis,color=Color.White,modifier=Modifier.align(Alignment.BottomStart).padding(10.dp))
                    }
                }
            }
        }
    }
}
