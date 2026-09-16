package org.kazumi.tv.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class ExternalTarget(val component:ComponentName,val label:String,val mx:Boolean)

object ExternalPlayback {
    private val mxPackages=setOf("com.mxtech.videoplayer.pro","com.mxtech.videoplayer.ad")
    fun validate(request:PlaybackRequest) {
        require(request.offlineId==null) { "离线缓存只能在应用内播放" }
        val url=request.url.toHttpUrlOrNull()
        require(url!=null && url.username.isEmpty() && url.password.isEmpty()) { "此地址不能交给外部播放器" }
        require(request.url.length<=16384 && request.headers.size<=64 && request.headers.entries.sumOf { it.key.length+it.value.length }<=32768) { "播放请求过大" }
        require(request.headers.all { (k,v)->k.isNotBlank() && k.all { it.code in 33..126 && it!=':' } && v.none { it=='\r' || it=='\n' || it=='\u0000' } }) { "播放请求头无效" }
    }
    private fun base(request:PlaybackRequest)=Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(request.url),request.mimeType?.takeIf { it.startsWith("video/") || it in listOf("application/x-mpegURL","application/vnd.apple.mpegurl","application/dash+xml") } ?: "video/*")
    fun targets(context:Context,request:PlaybackRequest):List<ExternalTarget> {
        validate(request)
        return context.packageManager.queryIntentActivities(base(request),PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo.exported && it.activityInfo.enabled && it.activityInfo.packageName!=context.packageName }
            .distinctBy { it.activityInfo.packageName }.map {
                val info=it.activityInfo; val mx=info.packageName in mxPackages
                val direct=if(mx)ComponentName(info.packageName,if(info.packageName.endsWith(".pro"))"com.mxtech.videoplayer.ActivityScreen" else "com.mxtech.videoplayer.ad.ActivityScreen") else ComponentName(info.packageName,info.name)
                val component=if(mx && runCatching { context.packageManager.getActivityInfo(direct,0).let { activity->activity.exported && activity.enabled } }.getOrDefault(false))direct else ComponentName(info.packageName,info.name)
                ExternalTarget(component,it.loadLabel(context.packageManager).toString(),mx)
            }.sortedBy { it.label }
    }
    fun intent(request:PlaybackRequest,target:ExternalTarget,position:Long):Intent {
        validate(request)
        require(target.mx || request.headers.isEmpty()) { "此来源需要请求头，请选择 MX Player 或使用内置播放器" }
        return base(request).setComponent(target.component).putExtra("title",request.title).apply {
            if(target.mx) {
                putExtra("position",position.coerceIn(0,Int.MAX_VALUE.toLong()).toInt())
                putExtra("return_result",true)
                putExtra("secure_uri",true)
                putExtra("sticky",false)
                putExtra("video_list",arrayOf(Uri.parse(request.url)))
                putExtra("headers",request.headers.flatMap { listOf(it.key,it.value) }.toTypedArray())
            }
        }
    }
    fun returnedPosition(request:PlaybackRequest,mx:Boolean,code:Int,result:Intent?):Long? {
        if(!mx || code!=android.app.Activity.RESULT_OK || result?.action!="com.mxtech.intent.result.VIEW" || result.data?.toString()!=request.url)return null
        return runCatching { result.getIntExtra("position",-1).toLong().takeIf { it>=0 } }.getOrNull()
    }
}
