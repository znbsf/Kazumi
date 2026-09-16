package org.kazumi.tv.download

import java.io.IOException

enum class DownloadFailure(val label:String) {
    LIVE("直播或尚未结束的清单暂不支持离线下载"),
    DRM("此内容需要尚未接入的离线授权或加密方式"),
    MANIFEST_LIMIT("下载清单过大或轨道过多"),
    MANIFEST_INVALID("下载清单无效或没有可下载的媒体"),
    RANGE("无法安全续传，请从头重新下载"),
    SPACE("存储空间不足，请清理部分离线内容"),
    ADDRESS("来源拒绝访问或地址已失效，可尝试重新解析地址"),
    NETWORK("网络读取失败，可稍后重试"),
    OTHER("下载失败，可重试或重新解析地址");
    companion object { fun stored(value:String?)=entries.firstOrNull { it.name==value } ?: OTHER }
}
class DownloadRejected(val reason:DownloadFailure):IOException(reason.label)

object HlsEncryptionPolicy {
    fun validate(text:String) {
        text.lineSequence().map { it.trim() }.filter { it.startsWith("#EXT-X-KEY:") || it.startsWith("#EXT-X-SESSION-KEY:") }.forEach { line ->
            val attributes=attributes(line.substringAfter(':'))
            if(attributes["METHOD"] !in listOf("NONE","AES-128") || attributes["KEYFORMAT"]?.let { it!="identity" }==true)throw DownloadRejected(DownloadFailure.DRM)
        }
    }
    private fun attributes(input:String):Map<String,String> {
        val result=mutableMapOf<String,String>(); var quoted=false; var start=0
        fun field(end:Int) {
            val part=input.substring(start,end).trim(); val delimiter=part.indexOf('=')
            if(delimiter<=0)throw DownloadRejected(DownloadFailure.MANIFEST_INVALID)
            val key=part.substring(0,delimiter); val raw=part.substring(delimiter+1)
            if(result.put(key,raw.removeSurrounding("\""))!=null)throw DownloadRejected(DownloadFailure.MANIFEST_INVALID)
        }
        input.forEachIndexed { index,ch -> if(ch=='"')quoted=!quoted else if(ch==',' && !quoted) { field(index); start=index+1 } }
        if(quoted)throw DownloadRejected(DownloadFailure.MANIFEST_INVALID)
        field(input.length); return result
    }
}
