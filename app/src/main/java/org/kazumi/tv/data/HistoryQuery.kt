package org.kazumi.tv.data

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class HistoryKind(val label: String) { ONLINE("在线"), OFFLINE("缓存") }
enum class HistoryGrouping(val label: String) { DATE("按日期"), SUBJECT("按作品") }
data class HistoryGroup(val key: String, val label: String, val entries: List<HistoryEntry>)
object HistoryQuery {
    fun filter(entries: List<HistoryEntry>,query: String="",kind: HistoryKind?=null): List<HistoryEntry> {
        val keyword=query.trim().lowercase(Locale.ROOT)
        return entries.filter { entry ->
            (kind==null || entry.kind==kind) && (keyword.isEmpty() ||
                listOf(entry.subject.title,entry.subject.metadata.originalTitle,entry.origin?.rule.orEmpty(),entry.episode)
                    .any { it.lowercase(Locale.ROOT).contains(keyword) })
        }.sortedWith(compareByDescending<HistoryEntry> { it.updatedAt }.thenBy { it.key })
    }
    fun groups(entries: List<HistoryEntry>,grouping: HistoryGrouping,now: Long=System.currentTimeMillis(),zone: TimeZone=TimeZone.getDefault()): List<HistoryGroup> {
        val ordered=filter(entries)
        if(grouping==HistoryGrouping.SUBJECT)return ordered.groupBy { it.subject.id }.map { (id,rows) -> HistoryGroup("subject:$id",rows.first().subject.title,rows) }
        fun calendar(time: Long)=Calendar.getInstance(zone).apply { timeInMillis=time }
        fun day(time: Long): String = calendar(time).let { "${it.get(Calendar.YEAR)}-${it.get(Calendar.MONTH)+1}-${it.get(Calendar.DAY_OF_MONTH)}" }
        val today=day(now)
        val yesterday=calendar(now).apply { add(Calendar.DAY_OF_MONTH,-1) }.let { day(it.timeInMillis) }
        return ordered.groupBy { if(it.updatedAt<=0) "unknown" else day(it.updatedAt) }.map { (key,rows) ->
            val label=when(key) { "unknown" -> "时间未知"; today -> "今天"; yesterday -> "昨天"; else -> calendar(rows.first().updatedAt).let { "${it.get(Calendar.YEAR)}年${it.get(Calendar.MONTH)+1}月${it.get(Calendar.DAY_OF_MONTH)}日" } }
            HistoryGroup("date:$key",label,rows)
        }
    }
}
