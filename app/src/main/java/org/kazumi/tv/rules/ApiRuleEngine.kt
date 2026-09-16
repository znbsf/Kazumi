package org.kazumi.tv.rules

import org.json.*
import java.net.URLEncoder
import java.net.URLDecoder

object JsonPath {
    fun read(root: Any?, path: String): List<Any> {
        require(path.startsWith('$')) { "JSONPath 必须以 $ 开头" }
        val token = Regex("\\.([A-Za-z0-9_$-]+)|\\[\\s*(\\d+|\\*|'(?:[^'\\\\]|\\\\.)*'|\"(?:[^\"\\\\]|\\\\.)*\")\\s*]")
        var offset = 1
        var values = listOfNotNull(root).filter { it != JSONObject.NULL }
        while (offset < path.length) {
            val match = token.find(path, offset)
            require(match != null && match.range.first == offset) { "不支持的 JSONPath：$path" }
            val dot = match.groupValues[1]; val bracket = match.groupValues[2]
            values = values.flatMap { value ->
                when {
                    bracket == "*" && value is JSONArray -> List(value.length()) { value.opt(it) }
                    bracket == "*" && value is JSONObject -> value.keys().asSequence().map { value.opt(it) }.toList()
                    bracket.toIntOrNull() != null && value is JSONArray -> listOfNotNull(value.opt(bracket.toInt()))
                    value is JSONObject -> {
                        val key = if (dot.isNotEmpty()) dot else bracket.drop(1).dropLast(1).replace("\\'", "'").replace("\\\"", "\"").replace("\\\\", "\\")
                        listOfNotNull(value.opt(key))
                    }
                    else -> emptyList()
                }.filterNotNull().filter { it != JSONObject.NULL }
            }
            offset = match.range.last + 1
        }
        return values
    }
    fun text(root: Any?, path: String): String = if (path.isBlank()) "" else read(root, path).firstOrNull()?.toString()?.trim().orEmpty()
}

data class ApiRequest(val url: String, val method: String, val headers: Map<String, String>, val body: String?)

class ApiRuleEngine {
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    fun template(value: String, variables: Map<String, Any>, encoded: Boolean = false): String =
        Regex("(?<![A-Za-z0-9_])@([A-Za-z_][A-Za-z0-9_]*)").replace(value) { match ->
            val found = requireNotNull(variables[match.groupValues[1]]) { "缺少模板变量 ${match.value}" }.toString()
            if (encoded) encode(found) else found
        }
    private fun render(value: Any?, variables: Map<String, Any>): Any? = when (value) {
        is JSONObject -> JSONObject().apply { value.keys().forEach { put(it, render(value.opt(it), variables)) } }
        is JSONArray -> JSONArray(List(value.length()) { render(value.opt(it), variables) })
        is String -> if (Regex("^@[A-Za-z_][A-Za-z0-9_]*$").matches(value)) requireNotNull(variables[value.drop(1)]) { "缺少模板变量 $value" } else template(value, variables)
        else -> value
    }
    private fun query(url: String, fields: JSONObject, variables: Map<String, Any>): String {
        if (fields.length() == 0) return url
        val merged = linkedMapOf<String, String>()
        url.substringBefore('#').substringAfter('?', "").split('&').filter { it.isNotBlank() }.forEach {
            merged[URLDecoder.decode(it.substringBefore('='), "UTF-8")] = URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }
        fields.keys().forEach { merged[template(it, variables)] = render(fields.opt(it), variables).toString() }
        val fragment = url.substringAfter('#', "").let { if (it.isBlank()) "" else "#$it" }
        return url.substringBefore('#').substringBefore('?') + "?" + merged.entries.joinToString("&") { encode(it.key) + "=" + encode(it.value) } + fragment
    }
    fun request(config: JSONObject, variables: Map<String, Any>): ApiRequest {
        val method = config.optString("method", "GET").uppercase(); require(method in listOf("GET", "POST")) { "仅支持 GET / POST" }
        val url = SourceRule.httpUrl(query(template(config.getString("url"), variables, true), config.optJSONObject("query") ?: JSONObject(), variables))
        val headers = mutableMapOf<String, String>()
        config.optJSONObject("headers")?.let { obj -> obj.keys().forEach { headers[template(it, variables)] = render(obj.opt(it), variables).toString() } }
        val body = if (method == "POST") when (config.optString("bodyType")) {
            "json" -> { headers["Content-Type"] = "application/json"; render(config.opt("body"), variables)?.toString() }
            "form" -> { headers["Content-Type"] = "application/x-www-form-urlencoded"; query("", config.optJSONObject("body") ?: JSONObject(), variables).removePrefix("?") }
            else -> null
        } else null
        return ApiRequest(url, method, headers, body)
    }
    fun search(config: JSONObject, raw: String): List<SourceMatch> = JsonPath.read(JSONTokener(raw).nextValue(), config.optString("listPath", "$.data[*]")).mapNotNull { row ->
        val title = JsonPath.text(row, config.optString("namePath", "$.name")); val source = JsonPath.text(row, config.optString("sourcePath", "$.url"))
        if (title.isBlank() || source.isBlank()) null else SourceMatch(title, source)
    }.distinctBy { it.url }
    fun chapters(rule: SourceRule, config: JSONObject, raw: String, source: String): List<Road> {
        val root = JSONTokener(raw).nextValue()
        val variables = mutableMapOf<String, Any>("source" to source)
        config.optJSONObject("variables")?.let { obj -> obj.keys().forEach { variables[it] = requireNotNull(JsonPath.read(root, obj.getString(it)).firstOrNull()) { "响应变量 $it 未匹配" } } }
        fun episode(name: String, url: String, road: Int, index: Int): Episode? {
            val page = config.optJSONObject("episodePage")
            val finalUrl = if (page != null) {
                val scope = variables + mapOf("episodeUrl" to url, "roadIndex" to road, "roadNumber" to road + 1, "episodeIndex" to index, "episodeNumber" to index + 1)
                query(template(page.getString("url"), scope, true), page.optJSONObject("query") ?: JSONObject(), scope)
            } else url
            if (finalUrl.isBlank()) return null
            return Episode(name.ifBlank { "第${index + 1}集" }, rule.resolve(finalUrl))
        }
        if (config.optString("format") == "delimited") {
            val rs = config.optString("roadSeparator", "$$$"); val es = config.optString("episodeSeparator", "#"); val fs = config.optString("fieldSeparator", "$")
            require(rs.isNotEmpty() && es.isNotEmpty() && fs.isNotEmpty()) { "分隔符不能为空" }
            val names = JsonPath.text(root, config.getString("roadNamesPath")).split(rs)
            return JsonPath.text(root, config.getString("roadEpisodesPath")).split(rs).mapIndexedNotNull { road, entries ->
                val episodes = entries.split(es).mapIndexedNotNull { index, value ->
                    val pos = value.indexOf(fs)
                    if (pos < 0) null else episode(value.substring(0, pos), value.substring(pos + fs.length), road, index)
                }
                if (episodes.isEmpty()) null else Road(names.getOrNull(road)?.ifBlank { null } ?: "线路 ${road + 1}", episodes)
            }
        }
        val path = config.optString("roadsPath", "$.data.roads[*]")
        return (if (path.isBlank()) listOf(root) else JsonPath.read(root, path)).mapIndexedNotNull { road, row ->
            val episodes = JsonPath.read(row, config.optString("episodesPath", "$.episodes[*]")).mapIndexedNotNull { index, item ->
                episode(JsonPath.text(item, config.optString("episodeNamePath", "$.name")), JsonPath.text(item, config.optString("episodeUrlPath", "$.url")), road, index)
            }
            if (episodes.isEmpty()) null else Road(JsonPath.text(row, config.optString("roadNamePath", "$.name")).ifBlank { "线路 ${road + 1}" }, episodes)
        }
    }
}
