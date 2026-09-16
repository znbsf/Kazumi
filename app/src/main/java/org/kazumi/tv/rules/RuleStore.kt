package org.kazumi.tv.rules

import android.content.Context
import org.json.*

class RuleStore(private val context: Context) {
    private val values = context.getSharedPreferences("tv_rules", Context.MODE_PRIVATE)
    companion object { private val lock = Any() }
    private fun imported(): List<SourceRule> {
        val raw = values.getString("imported", "[]") ?: "[]"
        val result = mutableListOf<SourceRule>()
        var damaged = false
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) try { result += SourceRule(array.getJSONObject(i)) } catch (_: Exception) { damaged = true }
        } catch (_: Exception) { damaged = true }
        if (damaged) {
            val edit = values.edit().putString("warning", "部分规则损坏，已隔离原始副本；其余规则仍可使用")
            if (!values.contains("damaged")) edit.putString("damaged", raw)
            edit.apply()
        }
        return result
    }
    fun warning(): String? = values.getString("warning", null)
    fun all(): List<SourceRule> = synchronized(lock) {
        val hidden = values.getStringSet("hidden", emptySet()).orEmpty()
        val builtIn = listOf("7sefun.json", "DM84.json").mapNotNull { file -> runCatching {
            SourceRule(JSONObject(context.assets.open("rules/$file").bufferedReader().use { it.readText() }))
        }.getOrNull() }
        val order = runCatching { JSONArray(values.getString("order", "[]")).let { a -> List(a.length()) { a.getString(it) } } }.getOrDefault(emptyList())
        (builtIn + imported()).associateBy { it.name.lowercase() }.values.filter { it.name.lowercase() !in hidden }
            .sortedBy { order.indexOf(it.name.lowercase()).takeIf { n -> n >= 0 } ?: Int.MAX_VALUE }
    }
    fun isEnabled(rule: SourceRule) = rule.name.lowercase() !in values.getStringSet("disabled", emptySet()).orEmpty()
    fun enabled() = all().filter { isEnabled(it) }
    fun toggle(rule: SourceRule) = synchronized(lock) {
        if (!isEnabled(rule)) rule.validateImport()
        val disabled = values.getStringSet("disabled", emptySet()).orEmpty().toMutableSet()
        if (!disabled.add(rule.name.lowercase())) disabled.remove(rule.name.lowercase())
        values.edit().putStringSet("disabled", disabled).apply()
    }
    private fun save(rules: List<SourceRule>) { values.edit().putString("imported", JSONArray(rules.map { it.json }).toString()).apply() }
    fun importReport(raw: String): RuleImportResult = synchronized(lock) {
        val report = RuleImport.parse(raw)
        if (report.rules.isEmpty()) return@synchronized report
        val old = imported()
        val combined = (old + report.rules).associateBy { it.name.lowercase() }.values.toList()
        require(combined.size <= 64) { "最多保存 64 条自定义规则" }
        val previous = all().associateBy { it.name.lowercase() }
        val versions = runCatching { JSONObject(values.getString("previous", "{}")!!) }.getOrDefault(JSONObject())
        report.rules.forEach { incoming -> previous[incoming.name.lowercase()]?.let { versions.put(incoming.name.lowercase(), it.json) } }
        val hidden = values.getStringSet("hidden", emptySet()).orEmpty().toMutableSet()
        report.rules.forEach { hidden.remove(it.name.lowercase()) }
        values.edit().putString("previous", versions.toString()).putStringSet("hidden", hidden)
            .putString("imported", JSONArray(combined.map { it.json }).toString()).apply()
        report
    }
    fun importJson(raw: String): Int {
        val report = importReport(raw)
        require(report.rules.isNotEmpty()) { report.failures.joinToString("\n") }
        return report.rules.size
    }
    fun delete(rule: SourceRule) = synchronized(lock) {
        val hidden = values.getStringSet("hidden", emptySet()).orEmpty().toMutableSet().apply { add(rule.name.lowercase()) }
        values.edit().putStringSet("hidden", hidden).apply()
        save(imported().filterNot { it.name.equals(rule.name, true) })
    }
    fun move(rule: SourceRule, offset: Int) = synchronized(lock) {
        val ordered = all().map { it.name.lowercase() }.toMutableList()
        val from = ordered.indexOf(rule.name.lowercase()); val to = from + offset
        if (from >= 0 && to in ordered.indices) {
            val item = ordered.removeAt(from); ordered.add(to, item)
            values.edit().putString("order", JSONArray(ordered).toString()).apply()
        }
    }
    fun restoreBuiltIns() { values.edit().putStringSet("hidden", emptySet()).apply() }
    fun rollback(rule: SourceRule) = synchronized(lock) {
        val previous = JSONObject(values.getString("previous", "{}")!!)
        val json = previous.optJSONObject(rule.name.lowercase()) ?: error("没有可恢复的上一版本")
        val restored = SourceRule(json).also { it.validateImport() }
        val rules = (imported().filterNot { it.name.equals(rule.name, true) } + restored)
        save(rules)
        previous.remove(rule.name.lowercase()); values.edit().putString("previous", previous.toString()).apply()
    }
}
