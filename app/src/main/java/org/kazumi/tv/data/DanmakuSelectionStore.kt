package org.kazumi.tv.data

import android.content.Context
import org.json.JSONObject

data class DanmakuSelection(val episode: DanmakuEpisode?=null,val offset:Long=0)
/** Exact media identity only; never carries a manual choice across episodes or seasons. */
class DanmakuSelectionStore(context:Context) {
    private val values=context.getSharedPreferences("danmaku_selections",0)
    private fun key(subject:Int,media:String)=java.security.MessageDigest.getInstance("SHA-256")
        .digest("$subject|$media".toByteArray()).joinToString("") { "%02x".format(it) }
    fun read(subject:Int,media:String):DanmakuSelection = runCatching {
        val row=JSONObject(values.getString(key(subject,media),"{}")!!)
        DanmakuSelection(row.optLong("id").takeIf { it>0 }?.let { DanmakuEpisode(it,row.optString("title")) },row.optLong("offset").coerceIn(-120000,120000))
    }.getOrDefault(DanmakuSelection())
    @Synchronized fun save(subject:Int,media:String,selection:DanmakuSelection) {
        require(subject>0 && media.isNotBlank())
        val target=key(subject,media)
        val editor=values.edit()
        if(!values.contains(target) && values.all.size>=100) {
            val oldest=values.all.minByOrNull { (_,value) -> runCatching { JSONObject(value as String).optLong("updated") }.getOrDefault(0) }?.key
            oldest?.let { editor.remove(it) }
        }
        editor.putString(target,JSONObject().put("id",selection.episode?.id).put("title",selection.episode?.title)
            .put("offset",selection.offset.coerceIn(-120000,120000)).put("updated",System.currentTimeMillis()).toString()).apply()
    }
    fun clear(subject:Int,media:String) { values.edit().remove(key(subject,media)).apply() }
}
