package org.kazumi.tv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import org.kazumi.tv.data.TvPreferences
import org.kazumi.tv.playback.*

@Composable
internal fun PlaybackPreferencesPanel(modifier: Modifier=Modifier) {
    val context=LocalContext.current
    val preferences=remember { TvPreferences(context) }
    var revision by remember { mutableIntStateOf(0) }
    val languages=listOf(null,"ja","zh","en","ko")
    fun label(value:String?)=when(value) { null -> "自动"; "ja" -> "日语"; "zh" -> "中文"; "en" -> "英语"; "ko" -> "韩语"; else -> value }
    fun next(value:String?)=languages[(languages.indexOf(value)+1).coerceAtLeast(0)%languages.size]
    @Suppress("UNUSED_VARIABLE") val observedRevision=revision
    Column(modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("播放偏好",style=KazumiType.heading)
        Text("下次打开视频生效。播放器内选择倍速、画面比例和可识别的轨道语言也会保存。",style=KazumiType.caption)
        PlayerAction("默认倍速：${preferences.speed}×") { preferences.speed=PlaybackOptions.speeds[(PlaybackOptions.speeds.indexOf(preferences.speed)+1)%PlaybackOptions.speeds.size]; revision++ }
        PlayerAction("画面比例：${preferences.pictureMode.label}") { preferences.pictureMode=PictureMode.entries[(preferences.pictureMode.ordinal+1)%PictureMode.entries.size]; revision++ }
        PlayerAction("音轨语言：${label(preferences.audioLanguage)}") { preferences.audioLanguage=next(preferences.audioLanguage); revision++ }
        PlayerAction("字幕：${if(preferences.subtitles) "开启" else "关闭"}") { preferences.subtitles=!preferences.subtitles; revision++ }
        PlayerAction("字幕语言：${label(preferences.textLanguage)}") { preferences.textLanguage=next(preferences.textLanguage); revision++ }
        Text("语言不存在时由播放器回退选择。没有语言信息的手选轨道仅用于当前视频，不保存轨道编号。",style=KazumiType.caption)
        PlayerAction("自动续播：${if(preferences.resumePlayback) "开启" else "关闭"}") { preferences.resumePlayback=!preferences.resumePlayback; revision++ }
        PlayerAction("快进退：${preferences.seekSeconds}秒") { preferences.seekSeconds=PlaybackOptions.seekSeconds[(PlaybackOptions.seekSeconds.indexOf(preferences.seekSeconds)+1)%PlaybackOptions.seekSeconds.size]; revision++ }
        PlayerAction("控制栏隐藏：${preferences.controlsSeconds}秒") { preferences.controlsSeconds=if(preferences.controlsSeconds==10)1 else preferences.controlsSeconds+1; revision++ }
    }
}
