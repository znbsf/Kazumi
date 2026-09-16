@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package org.kazumi.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import kotlinx.coroutines.CancellationException
import org.kazumi.tv.data.*
import org.kazumi.tv.domain.PlaybackIdentity
import org.kazumi.tv.playback.WebMediaResolver
import org.kazumi.tv.rules.*

/** All entry points use the same selection, retry, road and episode ownership. */
@Composable
fun PlaybackSessionScreen(subject: Subject, ruleName: String, initialEpisode: Episode,
                          initialRoads: List<Road> = emptyList(), initialRoad: Int = 0,
                          initialOrigin: PlaybackOrigin? = null,
                          onSelection: (Episode) -> Unit = {}, sourceCatalog:SourceCatalog?=null,
                          resolveEpisode:(suspend (String,Episode)->org.kazumi.tv.playback.PlaybackRequest)?=null,
                          onClose: () -> Unit) {
    val displayModes=rememberDisplayModeSession()
    val context = LocalContext.current
    val repository = sourceCatalog ?: remember { RuleRepository(context) }
    var activeRule by remember { mutableStateOf(ruleName) }
    var sourceRevision by remember { mutableIntStateOf(0) }
    var roads by remember { mutableStateOf(initialRoads) }
    var road by remember { mutableIntStateOf(initialRoad) }
    var episode by remember { mutableStateOf(initialEpisode) }
    var origin by remember { mutableStateOf(initialOrigin) }
    var restoring by remember { mutableStateOf(initialRoads.isEmpty()) }
    var retry by remember { mutableIntStateOf(0) }
    var startPosition by remember { mutableStateOf<Long?>(null) }
    var startPlaying by remember { mutableStateOf(true) }
    var choosingRoad by remember { mutableStateOf(false) }
    var choosingSource by remember { mutableStateOf(false) }
    val episodes = roads.getOrNull(road)?.episodes.orEmpty()
    val index = episodes.indexOfFirst { it.pageUrl == episode.pageUrl }
    LaunchedEffect(activeRule,sourceRevision) {
        if (roads.isNotEmpty())return@LaunchedEffect
        try {
            val rule = repository.rules.firstOrNull { it.name == activeRule } ?: return@LaunchedEffect
            val found = initialOrigin?.takeIf { it.sourceUrl.isNotBlank() }?.let { SourceMatch(it.sourceTitle, it.sourceUrl) }
                ?: repository.search(rule, subject.title).filter { it.title.trim() == subject.title.trim() }.singleOrNull()
                ?: return@LaunchedEffect
            val restored = repository.chapters(rule, found)
            // Do not infer a season or episode from list position; require the persisted page.
            val selectedRoad = restored.indices.firstOrNull { r -> restored[r].episodes.any { it.pageUrl == initialEpisode.pageUrl } }
                ?: return@LaunchedEffect
            roads = restored; road = selectedRoad
            origin = PlaybackOrigin(activeRule, found.title, found.url, restored[selectedRoad].title)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Single episode remains usable while the catalogue is unavailable. */ }
        finally { restoring = false }
    }
    fun select(next: Episode, position: Long? = null) {
        episode = next; startPosition = position; startPlaying=true; retry = 0; if(activeRule==ruleName && origin?.sourceUrl==initialOrigin?.sourceUrl)onSelection(next)
    }
    if(choosingSource) {
        Box(Modifier.fillMaxSize().background(KazumiColors.background).padding(30.dp)) {
            SourceScreen(subject,SourceTransfer(episode,startPosition ?: 0L),onSelect={ selected ->
                activeRule=selected.ruleName; roads=selected.roads; road=selected.road; origin=selected.origin
                episode=selected.episode; startPosition=selected.position; startPlaying=true; retry=0; restoring=false; sourceRevision++; choosingSource=false
            },onCancel={ choosingSource=false },catalog=repository)
        }
        return
    }
    if (choosingRoad) {
        RoadSelectionScreen(roads,road,episode,startPosition,onBack={ choosingRoad=false }) { nextRoad,nextIndex,position ->
            road=nextRoad; origin=origin?.copy(roadTitle=roads[nextRoad].title)
            select(roads[nextRoad].episodes[nextIndex],position); choosingRoad=false
        }
        return
    }
    ResolvingPlayback("$activeRule|${episode.pageUrl}|$retry", "${subject.title} · ${episode.title}", resolve = {
        val rule = repository.rules.firstOrNull { it.name == activeRule } ?: error("来源未启用")
        (resolveEpisode?.invoke(activeRule,episode) ?: WebMediaResolver(context).resolve(episode.pageUrl, rule, "${subject.title} · ${episode.title}"))
            .copy(resumeKey = "$activeRule|${episode.pageUrl}")
    }, verification = { url, done ->
        val rule = repository.rules.firstOrNull { it.name == activeRule }
        if (rule != null) VerificationScreen(rule, url, done)
    }, onClose = onClose) { media ->
        PlayerScreen(media, subject, displaySession=displayModes,
            onPrevious = if (index > 0) ({ select(episodes[index - 1]) }) else null,
            onNext = if (index in 0 until episodes.lastIndex) ({ select(episodes[index + 1]) }) else null,
            episodes = episodes.map { it.title }, currentEpisode = index, episodeKeys=episodes.map { "$activeRule|${it.pageUrl}" },
            onEpisodeSelected = { select(episodes[it]) }, origin = origin, initialPosition = startPosition, initialPlayWhenReady=startPlaying,
            sessionNotice = if (restoring) "正在恢复集表…" else if (roads.isEmpty()) "集表暂未恢复，当前仅支持单集播放" else "",
            onResolveAgain = { position -> startPosition = position; retry++ },
            onChooseRoad = if (roads.size > 1) ({ position -> startPosition = position; choosingRoad = true }) else null,
            onChooseSource = { position,resumePlay -> startPosition=position; startPlaying=resumePlay; choosingSource=true },
            onClose = onClose)
    }
}
