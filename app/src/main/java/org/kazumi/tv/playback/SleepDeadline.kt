package org.kazumi.tv.playback

data class SleepStatus(val remainingMs:Long=0,val expired:Boolean=false,val lastDurationMs:Long=0)
class SleepDeadline {
    private var deadline:Long?=null
    var status=SleepStatus(); private set
    fun start(now:Long,duration:Long) {
        require(duration in 1..86_400_000)
        deadline=Math.addExact(now,duration)
        status=SleepStatus(duration,false,duration)
    }
    fun cancel() { deadline=null; status=status.copy(remainingMs=0,expired=false) }
    fun refresh(now:Long):Boolean {
        val end=deadline ?: return false
        val remaining=(end-now).coerceAtLeast(0)
        status=status.copy(remainingMs=remaining,expired=remaining==0L)
        if(remaining==0L)deadline=null
        return remaining==0L
    }
}
