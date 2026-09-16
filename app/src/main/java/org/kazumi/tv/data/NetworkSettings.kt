package org.kazumi.tv.data

import android.content.Context
import kotlinx.coroutines.flow.update

object NetworkSettings {
    private val revision=kotlinx.coroutines.flow.MutableStateFlow(0L)
    val catalogRevision: kotlinx.coroutines.flow.StateFlow<Long> = revision
    fun invalidateCatalog() { revision.update { it+1 } }
    private lateinit var context: Context
    fun initialize(value: Context) { context = value.applicationContext }
    private val values get() = context.getSharedPreferences("tv_settings", Context.MODE_PRIVATE)
    var catalogMirror: Boolean
        get() = values.getBoolean("catalog_mirror", true)
        set(value) { if(value != catalogMirror) { values.edit().putBoolean("catalog_mirror", value).apply(); invalidateCatalog() } }
    var rulesMirror: Boolean
        get() = values.getBoolean("rules_mirror", false)
        set(value) { values.edit().putBoolean("rules_mirror", value).apply() }
    val apiBase get() = if (catalogMirror) "https://api.bgmapi.com" else "https://api.bgm.tv"
    val rulesBase get() = if (rulesMirror) "https://raw.gitcode.com/gh_mirrors/ka/KazumiRules/raw/main/" else "https://raw.githubusercontent.com/Predidit/KazumiRules/main/"
}
