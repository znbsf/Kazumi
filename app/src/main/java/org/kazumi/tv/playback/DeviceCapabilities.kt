package org.kazumi.tv.playback

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.view.View

data class AudioOutputCapability(val id:Int,val type:Int,val channels:List<Int>,val encodings:List<Int>,val rates:List<Int>) {
    val label:String get()=when(type) {
        2 -> "内置扬声器"; 3 -> "有线耳机"; 4 -> "有线耳机"; 8 -> "蓝牙 A2DP"
        9 -> "HDMI"; 10 -> "HDMI ARC"; 11 -> "USB"; 13 -> "扩展坞"; 19 -> "辅助线路"; 29 -> "HDMI eARC"
        else -> "音频设备（类型 $type）"
    }
    val channelLabel:String get()=if(channels.isEmpty()) "系统未限定声道数" else channels.distinct().sorted().joinToString(" / ")+" 声道"
}
data class DeviceCapabilities(val hdr:List<Int>?,val outputs:List<AudioOutputCapability>?,val displayLabel:String?) {
    val hdrLabel:String get()=when {
        hdr==null -> "无法读取 HDR 能力"
        hdr.isEmpty() -> "系统未报告 HDR 支持"
        else -> hdr.distinct().joinToString(" / ") { when(it) {
            1 -> "Dolby Vision"; 2 -> "HDR10"; 3 -> "HLG"; 4 -> "HDR10+"; else -> "HDR 类型 $it"
        } }
    }
    companion object {
        @Suppress("DEPRECATION")
        fun read(context:Context,view:View):DeviceCapabilities {
            val display=view.display?.takeIf { it.isValid }
            val hdr=runCatching { display?.let { if(Build.VERSION.SDK_INT>=34)it.mode.supportedHdrTypes.toList() else it.hdrCapabilities.supportedHdrTypes.toList() } }.getOrNull()
            val mode=runCatching { display?.mode?.let { DisplayPreference.from(it.physicalWidth,it.physicalHeight,it.refreshRate)?.label } }.getOrNull()
            val audio=runCatching {
                (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).getDevices(AudioManager.GET_DEVICES_OUTPUTS).map {
                    AudioOutputCapability(it.id,it.type,it.channelCounts.toList(),it.encodings.toList(),it.sampleRates.toList())
                }.sortedBy { it.id }
            }.getOrNull()
            return DeviceCapabilities(hdr,audio,mode)
        }
        fun encodingName(value:Int):String=when(value) {
            2 -> "PCM 16-bit"; 3 -> "PCM 8-bit"; 4 -> "PCM Float"; 5 -> "AC-3"; 6 -> "E-AC-3"
            7 -> "DTS"; 8 -> "DTS-HD"; 13 -> "IEC61937"; 14 -> "Dolby TrueHD"; 18 -> "E-AC-3 JOC"
            21 -> "PCM 24-bit"; 22 -> "PCM 32-bit"; else -> "编码 $value"
        }
    }
}
