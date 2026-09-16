package org.kazumi.tv.data

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SearchPage(val offset: Int, val items: List<Subject>)
data class SearchState(val query: String = "", val sort: String = "match", val pages: List<SearchPage> = emptyList(),
    val loading: Boolean = false, val endReached: Boolean = false, val failedOffset: Int? = null) {
    val items get() = pages.flatMap { it.items }.distinctBy { it.id }
    val firstOffset get() = pages.firstOrNull()?.offset ?: 0
}

/** Five pages at most; generation guards also reject providers that ignore coroutine cancellation. */
class SearchPager(private val scope: CoroutineScope, private val loader: suspend (String,Int,String) -> List<Subject>) {
    private val mutable = MutableStateFlow(SearchState())
    val state = mutable.asStateFlow()
    private var generation = 0L
    private var job: Job? = null
    fun submit(query: String, sort: String = "match", offset: Int = 0) {
        require(sort in listOf("match","score") && offset >= 0 && offset % 20 == 0)
        generation++; job?.cancel()
        mutable.value = SearchState(query.trim().take(150),sort)
        if(mutable.value.query.isNotBlank()) load(offset)
    }
    fun next() {
        val current=mutable.value
        if(current.query.isBlank() || current.loading || current.endReached || current.failedOffset != null)return
        load(current.pages.lastOrNull()?.let { it.offset+20 } ?: 0)
    }
    fun previous() { val s=mutable.value; if(!s.loading && s.firstOffset > 0) load(s.firstOffset-20) }
    fun retry() { if(!mutable.value.loading) mutable.value.failedOffset?.let(::load) }
    fun pageFor(id: Int) = mutable.value.pages.firstOrNull { page -> page.items.any { it.id==id } }?.offset ?: mutable.value.firstOffset
    private fun load(offset: Int) {
        val token=generation
        val before=mutable.value
        mutable.value=before.copy(loading=true,failedOffset=null)
        job=scope.launch {
            try {
                val result=loader(before.query,offset,before.sort).take(20)
                ensureActive()
                if(token!=generation)return@launch
                val previous=before.pages.isNotEmpty() && offset < before.firstOffset
                val known=before.items.map { it.id }.toSet()
                if(!previous && before.pages.isNotEmpty() && result.isNotEmpty() && result.all { it.id in known }) {
                    mutable.value=before.copy(loading=false,failedOffset=offset)
                    return@launch
                }
                val all=(before.pages.filterNot { it.offset==offset } + SearchPage(offset,result)).sortedBy { it.offset }
                val pages=if(previous)all.take(5) else all.takeLast(5)
                mutable.value=before.copy(pages=pages,loading=false,failedOffset=null,
                    endReached=if(previous) false else result.size < 20)
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_:Exception) { if(token==generation)mutable.value=before.copy(loading=false,failedOffset=offset) }
        }
    }
}
