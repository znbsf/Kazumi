package org.kazumi.tv.data
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WeekSchedule(val days: List<List<Subject>>, val notice: String? = null)
object CalendarCodec {
    fun read(raw:String): WeekSchedule {
        val source=JSONArray(raw); val days=List(7) { mutableListOf<Subject>() }
        for(i in 0 until source.length()) {
            val entry=source.optJSONObject(i) ?: continue
            val day=entry.optJSONObject("weekday")?.optInt("id") ?: continue
            if(day !in 1..7)continue
            val rows=entry.optJSONArray("items") ?: continue
            require(rows.length()<=500) { "单日排期数据过多" }
            for(j in 0 until rows.length())runCatching {
                val row=rows.getJSONObject(j)
                require(row.optInt("type",2)==2)
                if(!row.has("date"))row.put("date",row.optString("air_date"))
                LibraryCodec.subject(LibraryCodec.subjectJson(CatalogCodec.subject(row)))
            }.getOrNull()?.let { days[day-1].add(it) }
        }
        return WeekSchedule(days.map { it.distinctBy { subject -> subject.id } })
    }
}
object SeasonCalendar {
    fun mirror(raw: String): WeekSchedule {
        val root=JSONObject(raw)
        require((1..7).any { root.optJSONArray("$it")!=null }) { "季度排期格式无效" }
        val normalized=JSONArray()
        for(day in 1..7) {
            val entries=root.optJSONArray("$day") ?: JSONArray()
            require(entries.length()<=500)
            val subjects=JSONArray()
            for(index in 0 until entries.length())entries.optJSONObject(index)?.optJSONObject("subject")?.let { subjects.put(it) }
            normalized.put(JSONObject().put("weekday",JSONObject().put("id",day)).put("items",subjects))
        }
        return CalendarCodec.read(normalized.toString())
    }
    suspend fun official(fetch: suspend(Int)->String): WeekSchedule {
        val days=List(7) { mutableListOf<Subject>() }; val seen=mutableSetOf<Int>()
        var complete=false; var skipped=0
        for(page in 0 until 20) {
            val root=JSONObject(fetch(page*20)); val rows=root.getJSONArray("data")
            require(rows.length()<=20) { "季度响应超出分页限制" }
            var added=0
            for(index in 0 until rows.length()) {
                val subject=runCatching {
                    val row=rows.getJSONObject(index); require(row.optInt("type",2)==2)
                    LibraryCodec.subject(LibraryCodec.subjectJson(CatalogCodec.subject(row)))
                }.getOrNull()
                if(subject==null) { skipped++; continue }
                if(!seen.add(subject.id))continue
                added++
                val day=AnimeSeason.weekday(subject.metadata.date)
                if(day==null)skipped++ else days[day].add(subject)
            }
            if(rows.length()<20 || (root.has("total") && root.getInt("total")<=(page+1)*20)) { complete=true; break }
            if(added==0)break
        }
        val notices=buildList {
            if(!complete)add("季度结果未全部读完，请刷新后重试。")
            if(skipped>0)add("已跳过 ${skipped} 条缺少有效资料或首播日期的记录。")
        }
        return WeekSchedule(days,notices.takeIf { it.isNotEmpty() }?.joinToString(" "))
    }
}
class CalendarRepository {
    companion object { private val cache=ExpiringLruCache<String,WeekSchedule>(3,600000) { android.os.SystemClock.elapsedRealtime() } }
    suspend fun load(refresh:Boolean=false,season:AnimeSeason?=null):WeekSchedule = withContext(Dispatchers.IO) {
        val revision=NetworkSettings.catalogRevision.value
        val base=NetworkSettings.apiBase; val mirror=NetworkSettings.catalogMirror
        val key="$revision|$base|${season?.start ?: "current"}"
        if(!refresh)cache.get(key)?.let { return@withContext it }
        val result=when {
            season==null -> CalendarCodec.read(HttpText.requestAsync("$base/calendar"))
            mirror -> SeasonCalendar.mirror(HttpText.requestAsync("https://api.kazumi.fyi/kazumi/v1/calendar/season?start=${season.start}&end=${season.end}"))
            else -> {
                val body=JSONObject().put("keyword", "").put("sort","rank").put("filter", JSONObject()
                    .put("type",JSONArray().put(2)).put("tag",JSONArray().put("日本"))
                    .put("air_date",JSONArray().put(">=${season.start}").put("<${season.end}"))).toString()
                SeasonCalendar.official { offset -> HttpText.requestAsync("$base/v0/search/subjects?limit=20&offset=$offset", "POST",mapOf("Content-Type" to "application/json"),body) }
            }
        }
        if(revision==NetworkSettings.catalogRevision.value && result.notice==null)cache.put(key,result)
        result
    }
}
object ScheduleQuery {
    fun filter(rows:List<Subject>,collections:List<CollectionEntry>,watching:Boolean,hideWatched:Boolean,hideAbandoned:Boolean,sort:Int):List<Subject> {
        val types=collections.associate { it.subject.id to it.type }
        val filtered=rows.filter { (!watching || types[it.id]==CollectionType.WATCHING) &&
            (!hideWatched || types[it.id]!=CollectionType.WATCHED) && (!hideAbandoned || types[it.id]!=CollectionType.ABANDONED) }
        return filtered.sortedWith(when(sort) {
            0 -> compareByDescending<Subject> { it.metadata.votes ?: 0 }.thenBy { it.id }
            1 -> compareByDescending<Subject> { it.metadata.score ?: 0.0 }.thenBy { it.id }
            else -> compareBy { it.id }
        })
    }
}
