package org.kazumi.tv.playback
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide timer: engine replacement never changes its deadline. Main-thread owned. */
class PlaybackSleepTimer {
    companion object { val shared by lazy { PlaybackSleepTimer() } }
    private val clock=SleepDeadline()
    private val handler=Handler(Looper.getMainLooper())
    private val listeners=linkedSetOf<()->Unit>()
    private val mutable=MutableStateFlow(SleepStatus())
    val state=mutable.asStateFlow()
    private val tick=object:Runnable { override fun run() { refresh(); if(state.value.remainingMs>0)handler.postDelayed(this,500) } }
    fun start(durationMs:Long) {
        clock.start(SystemClock.elapsedRealtime(),durationMs); mutable.value=clock.status
        handler.removeCallbacks(tick); handler.post(tick)
    }
    fun cancel() { handler.removeCallbacks(tick); clock.cancel(); mutable.value=clock.status }
    fun refresh():SleepStatus {
        val expired=clock.refresh(SystemClock.elapsedRealtime()); mutable.value=clock.status
        if(expired)listeners.toList().forEach { it() }
        return state.value
    }
    fun attach(pause:()->Unit):()->Unit {
        listeners.add(pause)
        if(refresh().expired)pause()
        return { listeners.remove(pause) }
    }
}
