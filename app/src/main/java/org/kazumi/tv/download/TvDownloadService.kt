@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package org.kazumi.tv.download
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.media3.exoplayer.offline.*
import androidx.media3.exoplayer.scheduler.Scheduler


class TvDownloadService:DownloadService(901,1000L) {
    override fun onCreate() {
        if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("offline","离线下载",NotificationManager.IMPORTANCE_LOW))
        super.onCreate()
    }
    override fun onStartCommand(intent:android.content.Intent?,flags:Int,startId:Int):Int {
        val result=super.onStartCommand(intent,flags,startId)
        OfflineDownloads.get(this).recoverPending()
        return result
    }
    override fun getDownloadManager()=OfflineDownloads.get(this).manager
    override fun getScheduler():Scheduler?=null
    override fun onTimeout(startId:Int,fgsType:Int) {
        OfflineDownloads.get(this).manager.pauseDownloads()
        stopSelf()
    }
    override fun getForegroundNotification(downloads:MutableList<Download>,notMetRequirements:Int):Notification =
        DownloadNotificationHelper(this,"offline").buildProgressNotification(this,android.R.drawable.stat_sys_download,null,"KazumiTV 离线下载",downloads,notMetRequirements)
}
