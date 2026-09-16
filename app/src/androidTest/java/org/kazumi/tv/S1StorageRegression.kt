package org.kazumi.tv

import android.content.Context
import android.content.ContextWrapper
import org.json.JSONArray
import org.json.JSONObject
import org.kazumi.tv.data.*

object S1StorageRegression {
    fun run(context: Context) {
        // Separate storage namespace: never seed fixtures into the user's library.
        val name = "s1_storage_fixture"
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(ignored: String, mode: Int) = context.getSharedPreferences(name, mode)
        }
        val prefs = isolated.getSharedPreferences("ignored", Context.MODE_PRIVATE)
        val first = HistoryEntry("fixture|https://example.org/ep/1", Subject(123,"fixture","",""), "第01集", 1000, 10000)
        val legacy = JSONArray().put(LibraryCodec.historyJson(first)).put(JSONObject().put("broken", true)).toString()
        try {
            prefs.edit().clear().putString("history", legacy).commit()
            val store = LibraryStore(isolated)
            check(store.history().single().key == first.key)
            check(store.warning() != null)
            check(prefs.getString("history_damaged", null) == legacy)
            val origin = PlaybackOrigin("fixture", "fixture", "https://example.org/title/1", "line")
            store.save(first.copy(position = 2000, origin = origin))
            check(prefs.getString("history_migration_backup", null) == legacy)
            check(JSONObject(prefs.getString("history", "")!!).getInt("version") == 1)
            check(LibraryStore(isolated).history().single().origin == origin)
            store.save(first.copy(position = 3000, origin = origin))
            prefs.edit().putString("history", "[{broken").commit()
            check(LibraryStore(isolated).history().single().position == 2000L)
            check(LibraryStore(isolated).history().single().origin == origin)
        } finally { prefs.edit().clear().commit(); context.deleteSharedPreferences(name) }
    }
}
