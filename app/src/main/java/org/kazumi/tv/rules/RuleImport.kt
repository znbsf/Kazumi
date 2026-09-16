package org.kazumi.tv.rules

import org.json.*
import okio.ByteString.Companion.decodeBase64
import java.net.URLDecoder

data class RuleImportResult(val rules: List<SourceRule>, val failures: List<String>, val duplicates: Int = 0) {
    fun summary() = "已导入 ${rules.size} 条，失败 ${failures.size} 条" + (if (duplicates > 0) "，同名合并 $duplicates 条" else "") +
        (if (failures.isEmpty()) "；尚未验证搜索和播放" else "\n" + failures.joinToString("\n"))
}

/** Original Kazumi JSON/Base64 share transport; decoding never executes content. */
object RuleImport {
    const val MAX_CHARS = 350000
    private fun share(text: String): JSONObject {
        val payload = text.trim().replaceFirst(Regex("^kazumi:(?://)?", RegexOption.IGNORE_CASE), "")
        val normalized = URLDecoder.decode(payload.replace("+", "%2B"), "UTF-8").replace(Regex("\\s"), "")
        val bytes = normalized.decodeBase64() ?: error("无效分享编码")
        require(bytes.size <= 262144) { "分享内容过大" }
        return JSONObject(bytes.utf8())
    }
    fun parse(raw: String): RuleImportResult {
        require(raw.length <= MAX_CHARS) { "导入内容超过大小限制" }
        val text = raw.trim()
        val decoded = runCatching { JSONTokener(text).nextValue() }.getOrNull()
        val entries: List<Any?> = when (decoded) {
            is JSONArray -> List(decoded.length()) { decoded.opt(it) }
            is JSONObject -> decoded.optJSONArray("rules")?.let { array -> List(array.length()) { array.opt(it) } } ?: listOf(decoded)
            else -> Regex("kazumi:(?://)?[^\\r\\n]+", RegexOption.IGNORE_CASE).findAll(text).map { it.value }.toList()
        }
        require(entries.size in 1..64) { "需要 1–64 条 JSON 规则或 kazumi:// 分享链接" }
        val unique = linkedMapOf<String, SourceRule>()
        val failures = mutableListOf<String>()
        var duplicates = 0
        entries.forEachIndexed { index, item ->
            try {
                val json = when (item) { is JSONObject -> item; is String -> share(item); else -> error("不是规则对象") }
                val rule = SourceRule(json).also { it.validateImport() }
                if (unique.put(rule.name.lowercase(), rule) != null) duplicates++
            } catch (failure: Exception) {
                val reason = if (failure is IllegalArgumentException) failure.message ?: "格式错误" else "JSON或规则字段无效"
                failures += "第 ${index + 1} 条：$reason"
            }
        }
        return RuleImportResult(unique.values.toList(), failures, duplicates)
    }
}
