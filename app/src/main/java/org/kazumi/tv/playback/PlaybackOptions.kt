package org.kazumi.tv.playback

import java.util.Locale

enum class PictureMode(val label: String) {
    FIT("自动"), CROP("裁切填充"), STRETCH("拉伸填充"), FOUR_THREE("4:3");
    companion object { fun read(value:String?)=entries.firstOrNull { it.name==value } ?: FIT }
}
object PlaybackOptions {
    val speeds=listOf(.5f,.75f,1f,1.25f,1.5f,2f)
    val seekSeconds=listOf(5,10,15,30,60)
    fun speed(value:Float)=value.takeIf { it in speeds } ?: 1f
    fun language(value:String?): String? {
        val tag=value?.trim()?.lowercase(Locale.ROOT)?.replace('_','-') ?: return null
        if(tag in listOf("","und","unknown"))return null
        return tag.takeIf { it.length<=35 && it.matches(Regex("[a-z]{2,3}(-[a-z0-9]{2,8})*")) }
    }
    fun sameLanguage(first:String?,second:String?): Boolean {
        fun primary(value:String?): String? {
            val tag=language(value) ?: return null
            return runCatching { Locale.forLanguageTag(tag).isO3Language }.getOrDefault(tag.substringBefore('-'))
        }
        val a=primary(first) ?: return false
        return a==primary(second)
    }
    fun frame(width:Float,height:Float,mode:PictureMode): Pair<Float,Float> {
        if(mode!=PictureMode.FOUR_THREE)return width to height
        val w=minOf(width,height*4/3); return w to w*3/4
    }
}
