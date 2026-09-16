package org.kazumi.tv.data

import org.json.JSONArray
import org.json.JSONObject

data class PlaybackOrigin(val rule: String, val sourceTitle: String, val sourceUrl: String, val roadTitle: String) : java.io.Serializable
data class HistoryEntry(val key: String, val subject: Subject, val episode: String, val position: Long, val duration: Long,
                        val origin: PlaybackOrigin? = null, val updatedAt: Long = 0, val kind: HistoryKind = HistoryKind.ONLINE) : java.io.Serializable
data class LibraryRead<T>(val records: List<T>, val rejected: Int = 0, val damagedContainer: Boolean = false)

/** Accepts the original array format and the versioned envelope. Bad rows do not hide good rows. */
object LibraryCodec {
    fun <T> read(raw: String, decode: (JSONObject) -> T): LibraryRead<T> {
        val array = try {
            if (raw.trimStart().startsWith("[")) JSONArray(raw)
            else JSONObject(raw).let { require(it.getInt("version") == 1); it.getJSONArray("records") }
        } catch (_: Exception) { return LibraryRead(emptyList(), damagedContainer = true) }
        val records = mutableListOf<T>()
        var rejected = 0
        for (i in 0 until array.length()) {
            try { records += decode(array.getJSONObject(i)) } catch (_: Exception) { rejected++ }
        }
        return LibraryRead(records, rejected)
    }
    fun write(records: List<JSONObject>): String = JSONObject().put("version", 1).put("records", JSONArray(records)).toString()
    fun subject(json: JSONObject): Subject = Subject(json.getInt("id"), json.getString("title"), json.optString("cover"), json.optString("summary"), SubjectMetadata.fromJson(json.optJSONObject("metadata")))
        .also { require(it.id > 0 && it.title.isNotBlank()) }
    fun subjectJson(subject: Subject): JSONObject = JSONObject().put("id", subject.id).put("title", subject.title).put("cover", subject.cover).put("summary", subject.summary).put("metadata",subject.metadata.toJson())
    fun history(json: JSONObject): HistoryEntry {
        val origin = json.optJSONObject("origin")?.let {
            PlaybackOrigin(it.getString("rule"), it.getString("sourceTitle"), it.getString("sourceUrl"), it.getString("roadTitle"))
        }
        return HistoryEntry(json.getString("key"), subject(json), json.getString("episode"), json.getLong("position"), json.getLong("duration"), origin, json.optLong("updatedAt"), HistoryKind.entries.firstOrNull { it.name==json.optString("kind","ONLINE") } ?: error("Unknown history kind"))
            .also { require(it.key.isNotBlank() && it.episode.isNotBlank() && it.position >= 0 && it.duration >= 0) }
    }
    fun historyJson(entry: HistoryEntry): JSONObject = subjectJson(entry.subject)
        .put("key", entry.key).put("episode", entry.episode).put("position", entry.position).put("duration", entry.duration).put("updatedAt", entry.updatedAt).put("kind",entry.kind.name)
        .apply { entry.origin?.let { put("origin", JSONObject().put("rule", it.rule).put("sourceTitle", it.sourceTitle).put("sourceUrl", it.sourceUrl).put("roadTitle", it.roadTitle)) } }
}
