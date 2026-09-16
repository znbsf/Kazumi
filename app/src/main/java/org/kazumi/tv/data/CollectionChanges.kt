package org.kazumi.tv.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class CollectionAction { ADD, UPDATE, DELETE }
data class CollectionChange(val id:String,val subjectId:Int,val action:CollectionAction,val timestamp:Long,val entry:CollectionEntry?)
data class CollectionMerge(val collections:List<CollectionEntry>,val changes:List<CollectionChange>)

/** Native counterpart of collect_sync_merger.dart: remote snapshot plus unseen local operations.
 * UUID identities avoid the upstream second-resolution key collision. Each put carries its snapshot.
 * This is directional reconciliation, not a timestamp-based multi-master CRDT.
 */
object CollectionChanges {
    const val LIMIT=10000
    fun read(raw:String):List<CollectionChange> {
        require(raw.length<=8_000_000) { "收藏变更记录超过大小限制" }
        val root=JSONObject(raw); require(root.getInt("version")==1) { "收藏变更版本不支持" }
        val rows=root.getJSONArray("changes"); require(rows.length()<=LIMIT) { "收藏变更记录已达容量限制" }
        val result=List(rows.length()) { i -> val j=rows.getJSONObject(i)
            CollectionChange(j.getString("id"),j.getInt("subjectId"),CollectionAction.valueOf(j.getString("action")),j.getLong("timestamp"),j.optJSONObject("entry")?.let(CollectionCodec::read))
        }
        validate(result); return result
    }
    fun write(changes:List<CollectionChange>):String {
        validate(changes)
        val raw=JSONObject().put("version",1).put("changes",JSONArray(changes.map { c ->
            JSONObject().put("id",c.id).put("subjectId",c.subjectId).put("action",c.action.name).put("timestamp",c.timestamp)
                .apply { c.entry?.let { put("entry",CollectionCodec.write(it)) } }
        })).toString()
        require(raw.length<=8_000_000) { "收藏变更记录超过大小限制" }; return raw
    }
    private fun validate(changes:List<CollectionChange>) {
        require(changes.size<=LIMIT) { "收藏变更记录已达容量限制，请先完成同步整理" }
        require(changes.distinctBy { it.id }.size==changes.size) { "收藏变更标识重复" }
        changes.forEach { c ->
            require(c.id.isNotBlank() && c.id.length<=100 && c.subjectId>0 && c.timestamp>=0)
            require(if(c.action==CollectionAction.DELETE)c.entry==null else c.entry?.subject?.id==c.subjectId)
        }
    }
    fun append(existing:List<CollectionChange>,before:List<CollectionEntry>,after:List<CollectionEntry>,now:Long):List<CollectionChange> {
        validate(existing)
        val old=before.associateBy { it.subject.id }; val next=after.associateBy { it.subject.id }
        var stamp=maxOf(now,existing.maxOfOrNull { it.timestamp } ?: 0)
        val additions=(old.keys+next.keys).sorted().mapNotNull { id ->
            if(old[id]==next[id])null else {
                stamp=Math.addExact(stamp,1)
                CollectionChange(UUID.randomUUID().toString(),id,when { next[id]==null->CollectionAction.DELETE; old[id]==null->CollectionAction.ADD; else->CollectionAction.UPDATE },stamp,next[id])
            }
        }
        return (existing+additions).also(::validate)
    }
    fun merge(remote:List<CollectionEntry>,remoteChanges:List<CollectionChange>,localChanges:List<CollectionChange>):CollectionMerge {
        validate(remoteChanges); validate(localChanges)
        require(remote.size<=200 && remote.distinctBy { it.subject.id }.size==remote.size)
        val known=remoteChanges.associateBy { it.id }
        localChanges.forEach { require(known[it.id]==null || known[it.id]==it) { "同一收藏变更标识内容冲突" } }
        val unseen=localChanges.filter { it.id !in known }.sortedWith(compareBy<CollectionChange> { it.timestamp }.thenBy { it.id })
        val result=remote.associateByTo(linkedMapOf()) { it.subject.id }
        unseen.forEach { c -> when(c.action) {
            CollectionAction.DELETE -> result.remove(c.subjectId)
            CollectionAction.ADD -> result[c.subjectId]=requireNotNull(c.entry)
            CollectionAction.UPDATE -> if(c.subjectId in result)result[c.subjectId]=requireNotNull(c.entry)
        } }
        require(result.size<=200) { "合并后收藏超过200条，未截断数据" }
        val changes=(remoteChanges+unseen).also(::validate)
        return CollectionMerge(result.values.toList(),changes)
    }
}
