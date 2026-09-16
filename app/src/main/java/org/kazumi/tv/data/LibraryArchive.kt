package org.kazumi.tv.data
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class LibraryArchive(val collections:List<CollectionEntry>,val history:List<HistoryEntry>,val createdAt:Long)
object LibraryArchiveCodec {
    const val MAX_CHARS=4_000_000
    fun write(value:LibraryArchive):String {
        val raw=JSONObject().put("format","KazumiTV-library").put("version",1).put("createdAt",value.createdAt)
            .put("collections",JSONArray(value.collections.map(CollectionCodec::write)))
            .put("history",JSONArray(value.history.map(LibraryCodec::historyJson))).toString()
        read(raw)
        return raw
    }
    fun read(raw:String):LibraryArchive {
        require(raw.length<=MAX_CHARS) { "备份超过大小限制" }
        val root=JSONObject(raw.removePrefix("\uFEFF"))
        require(root.getString("format")=="KazumiTV-library" && root.getInt("version")==1) { "不支持的备份格式或版本" }
        val collections=root.getJSONArray("collections"); val history=root.getJSONArray("history")
        require(collections.length()<=200 && history.length()<=100) { "备份记录超过当前容量" }
        val c=List(collections.length()) { CollectionCodec.read(collections.getJSONObject(it)) }
        val h=List(history.length()) { LibraryCodec.history(history.getJSONObject(it)) }
        require(c.distinctBy { it.subject.id }.size==c.size && h.distinctBy { it.key }.size==h.size) { "备份存在重复记录" }
        return LibraryArchive(c,h,root.getLong("createdAt").also { require(it>=0) })
    }
    fun fingerprint(favorites:String,history:String):String = MessageDigest.getInstance("SHA-256")
        .digest("${favorites.length}:$favorites${history.length}:$history".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
