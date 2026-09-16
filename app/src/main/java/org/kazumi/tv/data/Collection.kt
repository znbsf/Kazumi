package org.kazumi.tv.data

import org.json.JSONObject
import java.util.Locale

// Local Kazumi values; Bangumi wire values require an explicit mapper.
enum class CollectionType(val code: Int, val label: String) {
    WATCHING(1,"在看"), PLANNED(2,"想看"), ON_HOLD(3,"搁置"), WATCHED(4,"看过"), ABANDONED(5,"抛弃")
}
data class CollectionEntry(val subject: Subject, val type: CollectionType, val updatedAt: Long = 0)
enum class CollectionSort(val label: String) { RECENT("最近变更"), TITLE("番剧名称"), RATING("评分最高"), DATE("开播时间") }
object CollectionCodec {
    fun read(json: JSONObject): CollectionEntry {
        val code=json.optInt("collectionType",2)
        val type=CollectionType.entries.firstOrNull { it.code==code } ?: error("Unsupported collection type")
        return CollectionEntry(LibraryCodec.subject(json),type,json.optLong("collectionUpdatedAt").coerceAtLeast(0))
    }
    fun write(entry: CollectionEntry): JSONObject = LibraryCodec.subjectJson(entry.subject)
        .put("collectionType",entry.type.code).put("collectionUpdatedAt",entry.updatedAt)
}
object CollectionQuery {
    private fun dateKey(date: String): String {
        if(!date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))return ""
        return runCatching {
            java.util.GregorianCalendar().apply {
                clear(); isLenient=false
                set(date.substring(0,4).toInt(),date.substring(5,7).toInt()-1,date.substring(8,10).toInt())
                timeInMillis
            }
            date
        }.getOrDefault("")
    }
    fun results(entries: List<CollectionEntry>, type: CollectionType? = null, sort: CollectionSort = CollectionSort.RECENT, query: String = ""): List<CollectionEntry> {
        val terms=query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val matches=entries.filter { entry ->
            (type==null || entry.type==type) && terms.all { term ->
                (entry.subject.title+"\n"+entry.subject.metadata.originalTitle).lowercase(Locale.ROOT).contains(term)
            }
        }
        val comparator=when(sort) {
            CollectionSort.RECENT -> compareByDescending<CollectionEntry> { it.updatedAt }
            CollectionSort.TITLE -> compareBy { it.subject.title.lowercase(Locale.ROOT) }
            CollectionSort.RATING -> compareByDescending { it.subject.metadata.score ?: -1.0 }
            CollectionSort.DATE -> compareByDescending { dateKey(it.subject.metadata.date) }
        }
        return matches.sortedWith(comparator.thenByDescending { it.updatedAt }.thenBy { it.subject.id })
    }
}
