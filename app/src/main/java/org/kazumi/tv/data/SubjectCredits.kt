package org.kazumi.tv.data
import org.json.JSONArray
import org.json.JSONObject

data class CreditEntry(val id:Int,val name:String,val image:String,val job:String="",val episodes:String="",val summary:String="",val character:Boolean=false,val actors:List<CreditEntry> = emptyList()):java.io.Serializable {
    val key:String get() = "$character|$id|$job|$episodes"
}
object CreditCodec {
    private fun text(row:JSONObject,key:String)=if(row.isNull(key)) "" else row.optString(key).trim()
    fun entry(row:JSONObject,character:Boolean):CreditEntry {
        val id=row.getInt("id"); require(id>0)
        val name=text(row,"name"); require(name.isNotBlank())
        val images=row.optJSONObject("images")
        val image=images?.let { text(it,"large").ifBlank { text(it,"medium") } }.orEmpty()
            .takeIf { it.startsWith("https://") || it.startsWith("http://") }.orEmpty()
        val actors=row.optJSONArray("actors") ?: JSONArray()
        require(actors.length()<=100)
        return CreditEntry(id,name.take(500),image,text(row,"relation").take(200),text(row,"eps").take(2000),
            text(row,"summary").ifBlank { text(row,"short_summary") }.take(100000),character,
            if(character) (0 until actors.length()).mapNotNull { runCatching { entry(actors.getJSONObject(it),false) }.getOrNull() }.distinctBy { it.id } else emptyList())
    }
    fun read(raw:String,character:Boolean):List<CreditEntry> {
        val rows=JSONArray(raw); require(rows.length()<=2000) { "人员名单超出读取限制" }
        return (0 until rows.length()).mapNotNull { runCatching { entry(rows.getJSONObject(it),character) }.getOrNull() }.distinctBy { it.key }
    }
}
class CreditsRepository {
    suspend fun list(id:Int,characters:Boolean):List<CreditEntry> = CreditCodec.read(HttpText.requestAsync("${NetworkSettings.apiBase}/v0/subjects/$id/${if(characters) "characters" else "persons"}"),characters)
    suspend fun detail(entry:CreditEntry):CreditEntry {
        val result=CreditCodec.entry(JSONObject(HttpText.requestAsync("${NetworkSettings.apiBase}/v0/${if(entry.character) "characters" else "persons"}/${entry.id}")),entry.character)
        require(result.id==entry.id) { "人物资料不匹配" }
        return result.copy(job=entry.job,episodes=entry.episodes,actors=entry.actors)
    }
}

object CreditQuery {
    fun filter(rows:List<CreditEntry>,query:String):List<CreditEntry> {
        val terms=query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return rows.filter { row -> val text=listOf(row.name,row.job,row.episodes,row.actors.joinToString(" ") { it.name }).joinToString(" ")
            terms.all { text.contains(it,ignoreCase=true) } }
    }
}
