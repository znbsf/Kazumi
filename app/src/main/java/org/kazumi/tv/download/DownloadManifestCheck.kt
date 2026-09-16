@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package org.kazumi.tv.download

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.hls.playlist.*
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import java.util.concurrent.atomic.AtomicBoolean
import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException

class DownloadManifestCheck(private val factory:CacheDataSource.Factory,private val cancelled:AtomicBoolean) {
    fun check(request:DownloadRequest) {
        if(request.keySetId!=null)throw DownloadRejected(DownloadFailure.DRM)
        when(Util.inferContentTypeForUriAndMimeType(request.uri,request.mimeType)) {
            C.CONTENT_TYPE_HLS -> {
                val loaded=read(request.uri)
                val playlist=hls(loaded.uri,loaded.bytes,HlsPlaylistParser())
                if(playlist is HlsMultivariantPlaylist) {
                    if(playlist.sessionKeyDrmInitData.isNotEmpty())throw DownloadRejected(DownloadFailure.DRM)
                    if(playlist.mediaPlaylistUrls.isEmpty())throw DownloadRejected(DownloadFailure.MANIFEST_INVALID)
                    if(playlist.mediaPlaylistUrls.size>256)throw DownloadRejected(DownloadFailure.MANIFEST_LIMIT)
                    playlist.mediaPlaylistUrls.forEach { uri ->
                        val loadedChild=read(uri)
                        val child=hls(loadedChild.uri,loadedChild.bytes,HlsPlaylistParser(playlist,null))
                        if(child !is HlsMediaPlaylist)throw DownloadRejected(DownloadFailure.MANIFEST_INVALID)
                    }
                }
            }
            C.CONTENT_TYPE_DASH -> {
                val loaded=read(request.uri); val bytes=loaded.bytes
                try {
                    val text=bytes.toString(Charsets.UTF_8)
                    if(text.contains("<!DOCTYPE",ignoreCase=true))throw DownloadRejected(DownloadFailure.MANIFEST_INVALID)
                    val xml=android.util.Xml.newPullParser(); xml.setInput(bytes.inputStream(),null)
                    var tags=0
                    while(xml.eventType!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                        active()
                        if(xml.eventType==org.xmlpull.v1.XmlPullParser.DOCDECL)throw DownloadRejected(DownloadFailure.MANIFEST_INVALID)
                        if(xml.eventType==org.xmlpull.v1.XmlPullParser.START_TAG) {
                            if(++tags>20000)throw DownloadRejected(DownloadFailure.MANIFEST_LIMIT)
                            if(xml.name=="ContentProtection")throw DownloadRejected(DownloadFailure.DRM)
                        }
                        xml.nextToken()
                    }
                    val manifest=DashManifestParser().parse(loaded.uri,bytes.inputStream())
                    if(manifest.dynamic)throw DownloadRejected(DownloadFailure.LIVE)
                    if(manifest.periodCount==0)throw DownloadRejected(DownloadFailure.MANIFEST_INVALID)
                    var tracks=0
                    for(i in 0 until manifest.periodCount)manifest.getPeriod(i).adaptationSets.forEach { adaptation -> adaptation.representations.forEach {
                        if(++tracks>256)throw DownloadRejected(DownloadFailure.MANIFEST_LIMIT)
                        if(it.format.drmInitData!=null)throw DownloadRejected(DownloadFailure.DRM)
                    } }
                    if(tracks==0)throw DownloadRejected(DownloadFailure.MANIFEST_INVALID)
                } catch(error:DownloadRejected) { throw error }
                catch(error:InterruptedIOException) { throw error }
                catch(_:Exception) { throw DownloadRejected(DownloadFailure.MANIFEST_INVALID) }
            }
        }
        active()
    }
    private fun hls(uri:Uri,bytes:ByteArray,parser:HlsPlaylistParser):HlsPlaylist {
        active()
        try {
            HlsEncryptionPolicy.validate(bytes.toString(Charsets.UTF_8))
            val playlist=parser.parse(uri,bytes.inputStream())
            if(playlist is HlsMediaPlaylist) {
                if(!playlist.hasEndTag)throw DownloadRejected(DownloadFailure.LIVE)
                if(playlist.protectionSchemes!=null)throw DownloadRejected(DownloadFailure.DRM)
                if(playlist.segments.isEmpty())throw DownloadRejected(DownloadFailure.MANIFEST_INVALID)
            }
            return playlist
        } catch(error:DownloadRejected) { throw error }
        catch(_:Exception) { throw DownloadRejected(DownloadFailure.MANIFEST_INVALID) }
    }
    private fun active() { if(cancelled.get() || Thread.currentThread().isInterrupted)throw InterruptedIOException("cancelled") }
    private data class Loaded(val uri:Uri,val bytes:ByteArray)
    private fun read(uri:Uri):Loaded {
        active(); val source=factory.createDataSource(); val result=ByteArrayOutputStream()
        try {
            val length=source.open(DataSpec.Builder().setUri(uri).setFlags(DataSpec.FLAG_ALLOW_GZIP).build())
            if(length>MAX_BYTES)throw DownloadRejected(DownloadFailure.MANIFEST_LIMIT)
            val buffer=ByteArray(8192)
            while(true) {
                active(); val count=source.read(buffer,0,buffer.size); if(count<0)break
                if(result.size()+count>MAX_BYTES)throw DownloadRejected(DownloadFailure.MANIFEST_LIMIT)
                result.write(buffer,0,count)
            }
            return Loaded(source.uri ?: uri,result.toByteArray())
        } finally { source.close() }
    }
    companion object { const val MAX_BYTES=2*1024*1024 }
}
