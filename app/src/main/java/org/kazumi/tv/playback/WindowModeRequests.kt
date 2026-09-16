package org.kazumi.tv.playback
import android.os.Looper
import android.view.Window

/** Main-thread window ownership; stale player disposal must not restore over a newer owner. */
internal object WindowModeRequests {
    private data class Request(val token:Any,var id:Int,val valid:()->Set<Int>)
    private data class Entry(val original:Int,var last:Int,val requests:MutableList<Request>)
    private val entries=mutableMapOf<Window,Entry>()
    private fun main() { check(Looper.myLooper()==Looper.getMainLooper()) }
    fun acquire(window:Window,valid:()->Set<Int>):Lease {
        main(); val current=window.attributes.preferredDisplayModeId
        val entry=entries.getOrPut(window) { Entry(current,current,mutableListOf()) }
        val token=Any(); entry.requests.add(Request(token,current,valid)); return Lease(window,token)
    }
    private fun apply(window:Window,entry:Entry,id:Int,valid:Set<Int>) {
        val next=if(id==0 || id in valid)id else 0
        if(window.attributes.preferredDisplayModeId!=next)window.attributes=window.attributes.apply { preferredDisplayModeId=next }
        entry.last=next
    }
    class Lease internal constructor(private val window:Window,private val token:Any) {
        fun request(id:Int) {
            main(); val entry=entries[window] ?: return; val owner=entry.requests.firstOrNull { it.token===token } ?: return
            owner.id=id
            if(entry.requests.last()===owner)apply(window,entry,id,owner.valid())
        }
        fun close() {
            main(); val entry=entries[window] ?: return; val owner=entry.requests.firstOrNull { it.token===token } ?: return
            val wasTop=entry.requests.last()===owner; entry.requests.remove(owner)
            if(!wasTop)return
            if(entry.requests.isNotEmpty()) { val top=entry.requests.last(); apply(window,entry,top.id,top.valid()) }
            else {
                if(window.attributes.preferredDisplayModeId==entry.last)apply(window,entry,entry.original,owner.valid())
                entries.remove(window)
            }
        }
    }
}
