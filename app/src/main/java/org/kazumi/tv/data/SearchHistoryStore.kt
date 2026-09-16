package org.kazumi.tv.data

import android.content.Context
import org.json.JSONArray

class SearchHistoryStore(context: Context) {
    private val preferences=context.getSharedPreferences("search_history",Context.MODE_PRIVATE)
    fun read(): List<String> = runCatching {
        val array=JSONArray(preferences.getString("queries","[]"))
        (0 until minOf(array.length(),20)).mapNotNull { (array.opt(it) as? String)?.trim()?.take(150)?.takeIf(String::isNotBlank) }.distinct()
    }.getOrDefault(emptyList())
    fun add(query: String) {
        val clean=query.trim().take(150); if(clean.isBlank())return
        preferences.edit().putString("queries",JSONArray((listOf(clean)+read().filterNot { it==clean }).take(20)).toString()).apply()
    }
    fun clear() { preferences.edit().remove("queries").apply() }
}
