package org.kazumi.tv.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.kazumi.tv.data.*

class SearchViewModel @JvmOverloads constructor(private val saved: SavedStateHandle,
    loader: (suspend (String,Int,String) -> List<Subject>)? = null) : ViewModel() {
    private val repository=CatalogRepository()
    val pager=SearchPager(viewModelScope,loader ?: { query,offset,sort -> repository.search(query,offset,sort) })
    var text: String
        get()=saved["text"] ?: ""
        set(value) { saved["text"]=value.take(150) }
    var focusId: Int?
        get()=saved["focus"]
        set(value) { saved["focus"]=value }
    var scrollIndex=0
    var scrollOffset=0
    var restoreFocus=false
    init {
        var revision=NetworkSettings.catalogRevision.value
        viewModelScope.launch {
            NetworkSettings.catalogRevision.collect { next ->
                if(next!=revision) {
                    revision=next; repository.clearCache()
                    val current=pager.state.value
                    if(current.query.isNotBlank())submit(current.query,current.sort)
                }
            }
        }
        val query=saved.get<String>("query").orEmpty()
        if(query.isNotBlank()) {
            restoreFocus=focusId != null
            pager.submit(query,saved["sort"] ?: "match",(saved.get<Int>("offset") ?: 0).coerceAtLeast(0) / 20 * 20)
        }
    }
    fun submit(query: String,sort: String) {
        saved["query"]=query.trim().take(150); saved["sort"]=sort; saved["offset"]=0
        focusId=null; scrollIndex=0; scrollOffset=0; restoreFocus=false
        pager.submit(query,sort)
    }
    fun selected(id: Int,index: Int,offset: Int) {
        focusId=id; saved["offset"]=pager.pageFor(id)
        scrollIndex=index; scrollOffset=offset; restoreFocus=true
    }
}
