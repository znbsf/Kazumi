package org.kazumi.tv.domain

object EpisodeWindow {
    const val SIZE=50
    fun indices(count: Int,page: Int,descending: Boolean): List<Int> {
        if(count<=0)return emptyList()
        val start=page.coerceIn(0,(count-1)/SIZE)*SIZE
        return (start until minOf(start+SIZE,count)).map { if(descending)count-1-it else it }
    }
    fun pageOf(index: Int,count: Int,descending: Boolean): Int = if(count<=0)0 else (if(descending)count-1-index.coerceIn(0,count-1) else index.coerceIn(0,count-1))/SIZE
    fun locate(text: String,count: Int): Int? = text.toIntOrNull()?.takeIf { it in 1..count }?.minus(1)
}
