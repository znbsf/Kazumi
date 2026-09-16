package org.kazumi.tv.rules

interface SourceCatalog {
    val rules:List<SourceRule>
    suspend fun search(rule:SourceRule,keyword:String):List<SourceMatch>
    suspend fun chapters(rule:SourceRule,match:SourceMatch):List<Road>
}
