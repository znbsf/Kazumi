package org.kazumi.tv.download

import java.io.IOException

/** A resumed response must identify the same representation before any body enters the cache. */
object DownloadRangePolicy {
    fun strongTag(raw:String?):String?=raw?.takeIf { it.length in 2..4096 && it.first()=='"' && it.last()=='"' && it.substring(1,it.lastIndex).none { ch -> ch=='"' || ch.code<32 || ch.code==127 } }
    fun validate(position:Long,cached:Boolean,expected:String?,status:Int,tag:String?,range:String?,contentType:String?) {
        if(contentType?.substringBefore(';')?.trim()?.lowercase() in listOf("text/html","application/xhtml+xml","application/json"))throw DownloadRejected(DownloadFailure.MANIFEST_INVALID)
        if(status!=200 && status!=206)throw DownloadRejected(DownloadFailure.RANGE)
        if(cached && (expected==null || strongTag(tag)!=expected))throw DownloadRejected(DownloadFailure.RANGE)
        if(position>0 && status!=206)throw DownloadRejected(DownloadFailure.RANGE)
        if(status==206) {
            val values=Regex("bytes ([0-9]+)-([0-9]+)/(\\*|[0-9]+)").matchEntire(range.orEmpty())?.groupValues
                ?: throw DownloadRejected(DownloadFailure.RANGE)
            val start=values[1].toLongOrNull(); val end=values[2].toLongOrNull(); val total=if(values[3]=="*")null else values[3].toLongOrNull()
            if(start!=position || end==null || end<position || (values[3]!="*" && (total==null || total<=end)))throw DownloadRejected(DownloadFailure.RANGE)
        }
    }
}
