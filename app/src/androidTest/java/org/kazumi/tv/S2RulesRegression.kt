package org.kazumi.tv

import android.content.Context
import android.content.ContextWrapper
import org.json.*
import org.kazumi.tv.rules.*

object S2RulesRegression {
    fun run(context: Context) {
        val name = "s2_rules_fixture"
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(ignored: String, mode: Int) = context.getSharedPreferences(name, mode)
        }
        val prefs = isolated.getSharedPreferences("ignored", Context.MODE_PRIVATE)
        val fixture = JSONObject(context.assets.open("rules/7sefun.json").bufferedReader().use { it.readText() }).put("name","fixture").put("version","1")
        try {
            prefs.edit().clear().commit()
            val store = RuleStore(isolated)
            val mixed = JSONArray().put(fixture).put(JSONObject(fixture.toString()).put("api","99")).toString()
            val report = store.importReport(mixed)
            check(report.rules.size == 1 && report.failures.size == 1)
            store.importJson(JSONObject(fixture.toString()).put("version","2").toString())
            store.rollback(store.all().first { it.name == "fixture" })
            check(RuleStore(isolated).all().first { it.name == "fixture" }.json.getString("version") == "1")
            val invalid = store.importReport(JSONObject(fixture.toString()).put("api","99").toString())
            check(invalid.rules.isEmpty())
            check(store.all().first { it.name == "fixture" }.json.getString("version") == "1")
            val rule = store.all().first { it.name == "fixture" }
            store.toggle(rule); check(store.enabled().none { it.name == "fixture" })
            store.toggle(rule); check(store.enabled().any { it.name == "fixture" })
            store.move(rule,-1)
            check(RuleStore(isolated).all().indexOfFirst { it.name == "fixture" } == 1)
            store.delete(rule); check(store.all().none { it.name == "fixture" })
            val builtin = store.all().first { it.name == "DM84" }
            store.delete(builtin); check(store.all().none { it.name == "DM84" })
            store.restoreBuiltIns(); check(store.all().any { it.name == "DM84" })
            prefs.edit().putString("imported", JSONArray().put(fixture).put("broken").toString()).commit()
            check(store.all().any { it.name == "fixture" }); check(store.warning() != null)
            check(prefs.contains("damaged"))
        } finally { prefs.edit().clear().commit(); context.deleteSharedPreferences(name) }
    }
}
