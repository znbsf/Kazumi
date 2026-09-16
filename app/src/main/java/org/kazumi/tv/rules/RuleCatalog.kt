package org.kazumi.tv.rules

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.kazumi.tv.data.HttpText
import org.kazumi.tv.data.NetworkSettings
import java.net.URLEncoder

data class CatalogRule(val name: String, val version: String)
class RuleCatalog {
    suspend fun list(): List<CatalogRule> = withContext(Dispatchers.IO) {
        val entries = JSONArray(HttpText.requestAsync(NetworkSettings.rulesBase + "index.json"))
        (0 until minOf(entries.length(), 200)).mapNotNull {
            val entry = entries.optJSONObject(it) ?: return@mapNotNull null
            val name = entry.optString("name")
            if (name.isBlank() || name.length > 80 || name.any { c -> c == '/' || c == '\\' || c == '.' }) null
            else CatalogRule(name, entry.optString("version"))
        }.distinctBy { it.name.lowercase() }
    }
    suspend fun install(entry: CatalogRule, store: RuleStore): Int = withContext(Dispatchers.IO) {
        val raw = HttpText.requestAsync(NetworkSettings.rulesBase + URLEncoder.encode(entry.name, "UTF-8").replace("+", "%20") + ".json", maxChars = RuleImport.MAX_CHARS)
        require(JSONObject(raw).getString("name").equals(entry.name, true)) { "规则名称与目录不一致" }
        store.importJson(raw)
    }
}
