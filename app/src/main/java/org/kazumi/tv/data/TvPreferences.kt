package org.kazumi.tv.data

import android.content.Context

class TvPreferences(context: Context) {
    var displayMode: org.kazumi.tv.playback.DisplayPreference?
        get() = org.kazumi.tv.playback.DisplayPreference.read(values.getString("display_mode",null))
        set(value) { check(values.edit().putString("display_mode",value?.key).commit()) { "显示模式保存失败" } }

    var speed: Float
        get() = org.kazumi.tv.playback.PlaybackOptions.speed(values.getFloat("playback_speed",1f))
        set(value) { values.edit().putFloat("playback_speed",org.kazumi.tv.playback.PlaybackOptions.speed(value)).apply() }
    var pictureMode: org.kazumi.tv.playback.PictureMode
        get() = org.kazumi.tv.playback.PictureMode.read(values.getString("picture_mode",null))
        set(value) { values.edit().putString("picture_mode",value.name).apply() }
    var audioLanguage: String?
        get() = org.kazumi.tv.playback.PlaybackOptions.language(values.getString("audio_language",null))
        set(value) { values.edit().putString("audio_language",org.kazumi.tv.playback.PlaybackOptions.language(value)).apply() }
    var textLanguage: String?
        get() = org.kazumi.tv.playback.PlaybackOptions.language(values.getString("text_language",null))
        set(value) { values.edit().putString("text_language",org.kazumi.tv.playback.PlaybackOptions.language(value)).apply() }
    var subtitles: Boolean
        get() = values.getBoolean("subtitles",true)
        set(value) { values.edit().putBoolean("subtitles",value).apply() }
    var resumePlayback: Boolean
        get() = values.getBoolean("resume_playback",true)
        set(value) { values.edit().putBoolean("resume_playback",value).apply() }
    var seekSeconds: Int
        get() = values.getInt("seek_seconds",10).takeIf { it in org.kazumi.tv.playback.PlaybackOptions.seekSeconds } ?: 10
        set(value) { values.edit().putInt("seek_seconds",value.takeIf { it in org.kazumi.tv.playback.PlaybackOptions.seekSeconds } ?: 10).apply() }
    var controlsSeconds: Int
        get() = values.getInt("controls_seconds",5).coerceIn(1,10)
        set(value) { values.edit().putInt("controls_seconds",value.coerceIn(1,10)).apply() }

    var incognito: Boolean
        get() = values.getBoolean("incognito",false)
        set(value) { values.edit().putBoolean("incognito",value).apply() }
    var videoOutput: org.kazumi.tv.playback.VideoOutput
        get() = org.kazumi.tv.playback.VideoOutput.fromStored(values.getString("video_output", null))
        set(value) { values.edit().putString("video_output", value.name).apply() }
    var memoryMode: org.kazumi.tv.playback.MemoryMode
        get() = org.kazumi.tv.playback.MemoryMode.fromStored(values.getString("memory_mode", null))
        set(value) { values.edit().putString("memory_mode", value.name).apply() }
    private val values = context.getSharedPreferences("tv_settings", Context.MODE_PRIVATE)
    var setupComplete: Boolean
        get() = values.getBoolean("setup_complete", false)
        set(value) { values.edit().putBoolean("setup_complete", value).apply() }
    var danmakuEnabled: Boolean
        get() = values.getBoolean("danmaku_enabled", true)
        set(value) { values.edit().putBoolean("danmaku_enabled", value).apply() }
    var autoNext: Boolean
        get() = values.getBoolean("auto_next", true)
        set(value) { values.edit().putBoolean("auto_next", value).apply() }
    var oled: Boolean
        get() = values.getBoolean("oled", false)
        set(value) { values.edit().putBoolean("oled", value).apply() }
}
