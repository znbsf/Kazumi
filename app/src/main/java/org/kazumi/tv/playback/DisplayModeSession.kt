package org.kazumi.tv.playback
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.Window
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.kazumi.tv.data.TvPreferences

data class DisplaySnapshot(val modes:List<AvailableDisplayMode> = emptyList(),val active:AvailableDisplayMode?=null) {
    companion object {
        fun read(view:View):DisplaySnapshot {
            val display=view.display?.takeIf { it.isValid } ?: return DisplaySnapshot()
            fun mode(m:android.view.Display.Mode)=AvailableDisplayMode(m.modeId,DisplayPreference.from(m.physicalWidth,m.physicalHeight,m.refreshRate))
            return DisplaySnapshot(display.supportedModes.mapNotNull { runCatching { mode(it) }.getOrNull() }.filter { it.id>0 },runCatching { mode(display.mode) }.getOrNull())
        }
    }
}
data class DisplayModeState(val display:DisplaySnapshot=DisplaySnapshot(),val saved:DisplayPreference?=null,val trial:DisplayTrial?=null,val seconds:Long=0,val status:String="")

class DisplayModeSession(context:Context,private val window:Window,view:View,
                         private val probe:()->DisplaySnapshot={ DisplaySnapshot.read(view) },private val trialMillis:Long=15000) {
    private val preferences=TvPreferences(context)
    private val manager=context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val handler=Handler(Looper.getMainLooper())
    private val mutable=MutableStateFlow(DisplayModeState())
    val state=mutable.asStateFlow()
    private var lease:WindowModeRequests.Lease?=null
    private var saved:DisplayPreference?=null
    private var trial:DisplayTrial?=null
    private var status=""
    private val tick=Runnable { refresh() }
    private val listener=object:DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId:Int) { refresh() }
        override fun onDisplayRemoved(displayId:Int) { refresh() }
        override fun onDisplayChanged(displayId:Int) { refresh() }
    }
    private fun main() { check(Looper.myLooper()==Looper.getMainLooper()) }
    fun start() {
        main(); if(lease!=null)return
        saved=preferences.displayMode; trial=null
        lease=WindowModeRequests.acquire(window) { runCatching { probe().modes.map { it.id }.toSet() }.getOrDefault(emptySet()) }
        manager.registerDisplayListener(listener,handler); refresh()
    }
    fun refresh() {
        main(); val owner=lease ?: return
        handler.removeCallbacks(tick)
        val display=try { probe() } catch(_:Exception) { status="无法读取显示能力，暂用系统默认"; DisplaySnapshot() }
        val now=SystemClock.elapsedRealtime()
        trial?.let { candidate ->
            if(candidate.expired(now)) { trial=null; status="试用超时，已恢复原选择" }
            else if(candidate.choice!=null && DisplayModePolicy.requestedId(candidate.choice,display.modes)==0) { trial=null; status="试用模式已不可用，已恢复原选择" }
        }
        val choice=if(trial!=null)trial!!.choice else saved
        val id=DisplayModePolicy.requestedId(choice,display.modes)
        owner.request(id)
        val message=if(choice!=null && id==0)"已保存模式当前不可用，暂用系统默认" else status
        mutable.value=DisplayModeState(display,saved,trial,trial?.remainingSeconds(now) ?: 0,message)
        if(trial!=null)handler.postDelayed(tick,250)
    }
    fun preview(choice:DisplayPreference?) {
        main(); if(lease==null)return
        refresh()
        if(choice!=null && DisplayModePolicy.requestedId(choice,state.value.display.modes)==0) { status="模式已不可用，请重新选择"; refresh(); return }
        require(trialMillis in 100..60000)
        trial=DisplayTrial(choice,Math.addExact(SystemClock.elapsedRealtime(),trialMillis)); status=""; refresh()
    }
    fun confirm() {
        main(); refresh(); val selected=trial ?: return
        try { preferences.displayMode=selected.choice; saved=selected.choice; trial=null; status="显示选择已保存，实际模式由系统决定" }
        catch(_:Exception) { trial=null; status="保存失败，已恢复原选择" }
        refresh()
    }
    fun cancelPreview() { main(); if(trial!=null) { trial=null; status="已恢复原选择"; refresh() } }
    fun stop() {
        main(); if(lease==null)return
        handler.removeCallbacks(tick); manager.unregisterDisplayListener(listener); trial=null
        lease?.close(); lease=null
        mutable.value=state.value.copy(trial=null,seconds=0)
    }
}
