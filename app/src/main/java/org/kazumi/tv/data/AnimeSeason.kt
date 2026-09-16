package org.kazumi.tv.data

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

data class AnimeSeason(val year: Int, val quarter: Int) {
    init { require(year in 1900..2200 && quarter in 1..4) }
    val start: String get() = String.format(Locale.ROOT, "%04d-%02d-01", year, (quarter-1)*3+1)
    val end: String get() = String.format(Locale.ROOT, "%04d-%02d-01", if(quarter==4) year+1 else year, if(quarter==4) 1 else quarter*3+1)
    val label: String get() = "${year}年${listOf("冬","春","夏","秋")[quarter-1]}季"
    fun shift(delta: Int): AnimeSeason {
        val index=year*4+quarter-1+delta
        return AnimeSeason(Math.floorDiv(index,4),Math.floorMod(index,4)+1)
    }
    companion object {
        fun current(): AnimeSeason = Calendar.getInstance().let { AnimeSeason(it.get(Calendar.YEAR),it.get(Calendar.MONTH)/3+1) }
        fun weekday(date: String?): Int? = runCatching {
            require(date!=null && Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(date))
            val values=date.split('-').map { it.toInt() }
            val calendar=GregorianCalendar().apply { isLenient=false; clear(); set(values[0],values[1]-1,values[2]); timeInMillis }
            (calendar.get(Calendar.DAY_OF_WEEK)+5)%7
        }.getOrNull()
    }
}
