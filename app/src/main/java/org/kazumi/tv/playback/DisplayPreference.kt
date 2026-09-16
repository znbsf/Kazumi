package org.kazumi.tv.playback
import kotlin.math.roundToInt
import java.util.Locale

data class DisplayPreference(val width:Int,val height:Int,val milliHz:Int) {
    init { require(width in 1..16384 && height in 1..16384 && milliHz in 1000..1_000_000) }
    val key get()="v1:$width:$height:$milliHz"
    val label get()="$width × $height · ${String.format(Locale.ROOT,"%.3f",milliHz/1000.0).trimEnd('0').trimEnd('.')} Hz"
    companion object {
        fun from(width:Int,height:Int,hz:Float):DisplayPreference { require(hz.isFinite()); return DisplayPreference(width,height,(hz*1000).roundToInt()) }
        fun read(raw:String?):DisplayPreference? { if(raw==null)return null; return runCatching { val parts=raw.split(':'); require(parts.size==4 && parts[0]=="v1"); DisplayPreference(parts[1].toInt(),parts[2].toInt(),parts[3].toInt()) }.getOrNull() }
    }
}
data class AvailableDisplayMode(val id:Int,val preference:DisplayPreference)
object DisplayModePolicy {
    fun requestedId(choice:DisplayPreference?,modes:List<AvailableDisplayMode>):Int =
        if(choice==null)0 else modes.filter { it.id>0 && it.preference==choice }.minByOrNull { it.id }?.id ?: 0
}
data class DisplayTrial(val choice:DisplayPreference?,val deadline:Long) {
    fun expired(now:Long)=now>=deadline
    fun remainingSeconds(now:Long)=((deadline-now).coerceAtLeast(0)+999)/1000
}
