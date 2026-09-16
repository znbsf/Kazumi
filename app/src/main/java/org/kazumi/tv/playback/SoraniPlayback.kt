package org.kazumi.tv.playback

import java.net.URI
import java.net.URLEncoder
import org.json.JSONObject
import org.json.JSONTokener
import org.kazumi.tv.data.HttpText
import org.kazumi.tv.rules.SourceRule
import kotlinx.coroutines.*

/** Compatibility adapter for Sorani's public episode API when its Svelte client cannot run on old TVs.
 * Mirrors the site's episode/play request. Server permission and preview decisions remain authoritative.
 */
object SoraniPlayback {
    fun ruleRoute(pageUrl:String,rule:SourceRule):Pair<String,String>? {
        val uri=URI(pageUrl)
        if(uri.host !in setOf("www.sorani.net","sorani.net"))return null
        val request=rule.json.optJSONObject("chapterApiConfig")?.optJSONObject("request") ?: return null
        if(request.optString("url")!="https://api.sorani.cc/sorani-cms/api/video/@source")return null
        val match=Regex("/anime/mal/([0-9]+)/episode/([0-9]+(?:\\.[0-9]+)?)/?").matchEntire(uri.path) ?: return null
        if(!uri.rawQuery.isNullOrBlank())return null // Unknown line/variant parameters must not silently select another stream.
        return match.groupValues[1] to match.groupValues[2]
    }
    suspend fun resolveFromRule(pageUrl:String,rule:SourceRule,headers:Map<String,String>,title:String):PlaybackRequest? {
        val (video,order)=ruleRoute(pageUrl,rule) ?: return null
        val base="https://api.sorani.cc/sorani-cms"
        val origin=URI(pageUrl).let { "${it.scheme}://${it.authority}" }
        val requestHeaders=headers+mapOf("Referer" to pageUrl,"Origin" to origin)
        suspend fun get(path:String):JSONObject {
            val response=HttpText.pageAsync(base+path,headers=requestHeaders)
            if(response.status !in 200..299)throw MediaResolutionFailure("来源接口","HTTP ${response.status}")
            val json=runCatching { JSONObject(response.body) }.getOrElse { throw MediaResolutionFailure("来源接口","响应不是有效数据") }
            if(json.optInt("code")!=200)throw MediaResolutionFailure("来源接口","来源未成功返回数据")
            return json
        }
        return withTimeout(20000) {
            coroutineScope {
                val episodeCall=async { get("/api/video/episode/video/$video/episode/$order").getJSONObject("data") }
                val linesCall=async { get("/api/video/$video/play-lines").getJSONArray("data") }
                val episode=episodeCall.await();val lines=linesCall.await()
                if(episode.optLong("videoId")!=video.toLong()||episode.optDouble("episodeOrder")!=order.toDouble()||episode.optLong("episodeId")<=0)
                    throw MediaResolutionFailure("来源接口","返回的作品或集数不匹配")
                val enabled=(0 until lines.length()).map { lines.getJSONObject(it) }.filter { it.optBoolean("enable",true) }
                val selected=enabled.firstOrNull { it.optBoolean("isDefault") } ?: enabled.firstOrNull()
                    ?: throw MediaResolutionFailure("来源接口","没有可用线路")
                if(selected.optBoolean("parseEnabled"))return@coroutineScope null
                val code=selected.optString("code").takeIf { it.isNotBlank() } ?: throw MediaResolutionFailure("来源接口","缺少线路标识")
                val response=get("/api/video/episode/${episode.getLong("episodeId")}/play?lineCode=${URLEncoder.encode(code,"UTF-8")}")
                PlaybackRequest(permittedUrl(response),requestHeaders,title,mimeType=if(response.getJSONObject("data").optBoolean("hls"))"application/x-mpegURL" else null)
            }
        }
    }
    fun apiUrl(pageUrl:String,html:String):String? {
        val uri=URI(pageUrl)
        if(uri.host !in setOf("www.sorani.net","sorani.net") || !Regex("/anime/.+/episode/[0-9.]+/?").matches(uri.path))return null
        val baseLiteral=Regex("\"PUBLIC_API_BASE\"\\s*:\\s*(\"(?:[^\"\\\\]|\\\\.)*\")").find(html)?.groupValues?.get(1) ?: return null
        val base=JSONTokener(baseLiteral).nextValue().toString().trimEnd('/')
        if(URI(base).scheme!="https"||URI(base).host!="api.sorani.cc")return null
        val episode=Regex("episodeId:([0-9]+),episodeName:").findAll(html).map { it.groupValues[1] }.distinct().toList().singleOrNull() ?: return null
        val lineLiteral=Regex("selectedLineCode:(\"(?:[^\"\\\\]|\\\\.)*\")").find(html)?.groupValues?.get(1) ?: return null
        val line=JSONTokener(lineLiteral).nextValue().toString()
        return "$base/api/video/episode/$episode/play?lineCode=${URLEncoder.encode(line,"UTF-8") }"
    }
    fun permittedUrl(response:JSONObject):String {
        if(response.optInt("code")!=200 || response.optBoolean("success",true)==false)
            throw MediaResolutionFailure("来源接口","播放接口未成功返回")
        val data=response.optJSONObject("data") ?: throw MediaResolutionFailure("来源接口","缺少播放信息")
        if(!data.optBoolean("canPlay"))throw MediaResolutionFailure("来源权限","来源未允许播放，可能需要登录或会员")
        if(data.optBoolean("hasPreview")||data.optBoolean("advertisingRequired"))
            throw MediaResolutionFailure("来源权限","来源要求网页内完成试看或前置步骤")
        return runCatching { SourceRule.httpUrl(data.getString("playUrl")) }.getOrElse { throw MediaResolutionFailure("来源接口","没有有效的播放地址") }
    }
    suspend fun resolve(pageUrl:String,html:String,headers:Map<String,String>,title:String):PlaybackRequest? {
        val endpoint=apiUrl(pageUrl,html) ?: return null
        val origin=URI(pageUrl).let { "${it.scheme}://${it.authority}" }
        val requestHeaders=headers+mapOf("Referer" to pageUrl,"Origin" to origin)
        val response=JSONObject(HttpText.requestAsync(endpoint,headers=requestHeaders))
        val url=permittedUrl(response)
        return PlaybackRequest(url,requestHeaders,title,mimeType=if(response.getJSONObject("data").optBoolean("hls"))"application/x-mpegURL" else null)
    }
}
