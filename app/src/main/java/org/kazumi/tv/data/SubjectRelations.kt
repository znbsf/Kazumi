package org.kazumi.tv.data

import org.json.JSONArray

data class SubjectRelation(val relation: String, val subject: Subject)
data class RelationResult(val items: List<SubjectRelation>, val truncated: Boolean)
object SubjectRelations {
    fun decode(raw:String):List<SubjectRelation> {
        val rows=JSONArray(raw); require(rows.length()<=1000)
        return (0 until rows.length()).mapNotNull { index -> runCatching {
            val row=rows.getJSONObject(index); require(row.optInt("type")==2)
            val subject=LibraryCodec.subject(LibraryCodec.subjectJson(CatalogCodec.subject(row)))
            SubjectRelation(row.optString("relation").trim().ifBlank { "关联" },subject)
        }.getOrNull() }.distinctBy { it.subject.id }
    }
    // Port of reference bangumi_relation.dart: oldest prequels, nearest sequels,
    // then other direct anime. Never follow non-mainline edges recursively.
    suspend fun resolve(id:Int,maxDepth:Int=12,maxFetch:Int=20,fetch:suspend(Int)->List<SubjectRelation>):RelationResult {
        require(maxDepth>=1 && maxFetch>=1)
        val direct=fetch(id).filter { it.subject.id!=id }.distinctBy { it.subject.id }
        val byId=direct.associateByTo(linkedMapOf()) { it.subject.id }
        val directions=mutableMapOf<Int,String>()
        val layers=mapOf("前传" to mutableListOf<List<SubjectRelation>>(),"续集" to mutableListOf<List<SubjectRelation>>())
        val scheduled=mutableSetOf(id)
        var frontier=mutableListOf<Pair<Int,String>>()
        for(direction in layers.keys) {
            val layer=direct.filter { it.relation==direction && directions.putIfAbsent(it.subject.id,direction)==null }
            if(layer.isNotEmpty())layers.getValue(direction).add(layer)
            for(row in layer)if(scheduled.add(row.subject.id))frontier.add(row.subject.id to direction)
        }
        var count=1; var depth=1; var truncated=false
        while(frontier.isNotEmpty() && depth<maxDepth && count<maxFetch) {
            val next=mutableListOf<Pair<Int,String>>()
            val current=mapOf("前传" to mutableListOf<SubjectRelation>(),"续集" to mutableListOf<SubjectRelation>())
            val nodes=frontier.take(maxFetch-count)
            if(nodes.size<frontier.size)truncated=true
            for((subjectId,direction) in nodes) {
                val rows=fetch(subjectId); count++
                for(row in rows) {
                    if(row.relation!=direction || row.subject.id==id)continue
                    val display=byId.getOrPut(row.subject.id) { row }
                    if(directions.putIfAbsent(row.subject.id,direction)==null)current.getValue(direction).add(display)
                    if(scheduled.add(row.subject.id))next.add(row.subject.id to direction)
                }
            }
            for(direction in layers.keys)if(current.getValue(direction).isNotEmpty())layers.getValue(direction).add(current.getValue(direction))
            frontier=next; depth++
        }
        if(frontier.isNotEmpty())truncated=true
        return RelationResult(layers.getValue("前传").asReversed().flatten()+layers.getValue("续集").flatten()+direct.filter { it.subject.id !in directions },truncated)
    }
}
class RelationRepository {
    suspend fun load(id:Int):RelationResult = SubjectRelations.resolve(id) { next ->
        SubjectRelations.decode(HttpText.requestAsync("${NetworkSettings.apiBase}/v0/subjects/$next/subjects"))
    }
}
