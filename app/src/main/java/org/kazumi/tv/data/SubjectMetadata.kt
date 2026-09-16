package org.kazumi.tv.data

import org.json.JSONObject

data class SubjectMetadata(val originalTitle: String = "", val date: String = "", val platform: String = "",
    val score: Double? = null, val votes: Int? = null, val rank: Int? = null, val episodes: Int? = null) : java.io.Serializable {
    fun toJson(): JSONObject = JSONObject().put("originalTitle",originalTitle).put("date",date).put("platform",platform)
        .put("score",score).put("votes",votes).put("rank",rank).put("episodes",episodes)
    companion object {
        fun fromJson(json: JSONObject?): SubjectMetadata {
            if(json == null) return SubjectMetadata()
            fun text(key: String) = if(json.isNull(key)) "" else json.optString(key)
            fun positive(key: String) = json.optInt(key).takeIf { it > 0 }
            return SubjectMetadata(text("originalTitle"),text("date"),text("platform"),
                json.optDouble("score").takeIf { it.isFinite() && it > 0 && it <= 10 },positive("votes"),positive("rank"),positive("episodes"))
        }
    }
}

object CatalogCodec {
    fun subject(value: JSONObject): Subject {
        fun JSONObject.text(key: String) = if (isNull(key)) "" else optString(key).trim()
        val rating = value.optJSONObject("rating")
        fun positive(key: String) = rating?.optInt(key)?.takeIf { it > 0 }
        return Subject(value.getInt("id"), value.text("name_cn").ifBlank { value.text("nameCN") }.ifBlank { value.text("name") },
            value.optJSONObject("images")?.text("large").orEmpty().ifBlank { value.text("image") }, value.text("summary"),
            SubjectMetadata(value.text("name"), value.text("date").ifBlank { value.optJSONObject("airtime")?.text("date").orEmpty() },
                value.text("platform"), rating?.optDouble("score")?.takeIf { it.isFinite() && it > 0 && it <= 10 },
                positive("total"), positive("rank"), value.optInt("eps").takeIf { it > 0 }))
    }
}

object SynopsisText {
    /** Plain-text reading view. Rich comments and interactive BBCode are a separate migration task. */
    fun clean(source: String): String {
        val bounded = source.take(100_000)
            .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), "")
            .replace(Regex("(?i)<br\\s*/?>|</(?:p|div)>"), "\n")
            .replace(Regex("(?is)\\[img(?:=[^]]*)?].*?\\[/img]"), "[图片]")
            .replace(Regex("(?i)\\[/?(?:b|i|u|s|quote|code|url|color|size)(?:=[^]]*)?]"), "")
        val text = org.jsoup.Jsoup.parse(bounded).wholeText().replace("\r\n","\n").replace('\r','\n')
            .split('\n').joinToString("\n") { it.trim() }.replace(Regex("\n{3,}"),"\n\n").trim()
        return text + if(source.length > 100_000) "\n\n[简介过长，仅显示前100000字符]" else ""
    }
}
