package org.kazumi.tv.playback

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.kazumi.tv.rules.*
import org.kazumi.tv.data.AppHttp
import okhttp3.*
import org.json.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One isolated WebView per attempt; dynamic ES5 discovery and bounded, cancellable probes. */
class WebMediaResolver(private val context: Context, private val timeoutMs: Long = 25000,
    private val diagnostic: (String) -> Unit = {}) {
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolve(pageUrl: String, rule: SourceRule, title: String): PlaybackRequest = withContext(Dispatchers.Main) {
        SourceRule.httpUrl(pageUrl)
        val defaults = mapOf("Referer" to rule.referer, "User-Agent" to rule.userAgent)
        if (MediaAddress.isMedia(pageUrl)) return@withContext probe(PlaybackRequest(pageUrl,defaults,title))
        val candidates = Channel<PlaybackRequest>(24)
        val seen = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val fatal = AtomicReference<Exception?>(null)
        var scriptError = false
        var lastProbe: MediaResolutionFailure? = null
        val web = WebView(context)
        fun offer(url: String, headers: Map<String,String>) {
            if (runCatching { SourceRule.httpUrl(url) }.isSuccess && seen.size < 24 && seen.add(url))
                candidates.trySend(PlaybackRequest(url,headers,title))
        }
        var polling: Job? = null
        try {
            web.settings.javaScriptEnabled = true
            web.settings.domStorageEnabled = true
            web.settings.allowFileAccess = false
            web.settings.allowContentAccess = false
            web.settings.mediaPlaybackRequiresUserGesture = false
            web.settings.userAgentString = rule.userAgent
            web.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR &&
                        (message.message().contains("SyntaxError") || message.message().contains("Unexpected token"))) scriptError = true
                    return true // Do not copy remote console text, URLs or tokens into application logs.
                }
            }
            web.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = request.url.scheme !in listOf("http","https")
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if(request.isForMainFrame) fatal.set(MediaResolutionFailure("网页加载", "连接或证书错误（${error.errorCode}）"))
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if(request.isForMainFrame) fatal.set(MediaResolutionFailure("网页加载", "HTTP ${response.statusCode}"))
                }
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    if(MediaAddress.isMedia(request.url.toString())) {
                        val headers = request.requestHeaders.filterKeys { it.equals("Referer",true)||it.equals("User-Agent",true)||it.equals("Origin",true) }.toMutableMap()
                        if(headers.keys.none { it.equals("Referer",true) }) headers["Referer"] = pageUrl
                        if(headers.keys.none { it.equals("User-Agent",true) }) headers["User-Agent"] = rule.userAgent
                        offer(request.url.toString(),headers)
                    }
                    return null
                }
            }
            web.loadUrl(pageUrl,mapOf("Referer" to rule.referer))
            polling = launch {
                while(isActive) {
                    web.evaluateJavascript(MediaDiscoveryScript.poll) { raw ->
                        runCatching {
                            val snapshot = JSONObject(JSONTokener(raw).nextValue().toString())
                            if(SourcePageChecks.looksLikeChallengeTitle(snapshot.optString("title"))) fatal.set(SourceVerificationRequired(pageUrl))
                            val urls = snapshot.optJSONArray("urls") ?: JSONArray()
                            for(i in 0 until urls.length()) {
                                val media = urls.getJSONObject(i)
                                offer(media.getString("url"), mapOf("Referer" to media.optString("referer",pageUrl),"User-Agent" to rule.userAgent))
                            }
                            if (rule.json.optBoolean("useLegacyParser")) {
                                val frames = snapshot.optJSONArray("frames") ?: JSONArray()
                                for(i in 0 until frames.length()) {
                                    val frame = frames.getJSONObject(i)
                                    LegacyMediaAddress.extract(frame.getString("url")).forEach { url ->
                                        offer(url, mapOf("Referer" to frame.getString("url"), "User-Agent" to rule.userAgent))
                                    }
                                }
                            }
                        }
                    }
                    delay(300)
                }
            }
            val resolved = withTimeoutOrNull(timeoutMs) {
                var found: PlaybackRequest? = null
                while(found == null) {
                    fatal.get()?.let { throw it }
                    val candidate = withTimeoutOrNull(300) { candidates.receive() } ?: continue
                    try { found = probe(candidate) }
                    catch(cancelled: CancellationException) { throw cancelled }
                    catch(failure: MediaResolutionFailure) {
                        lastProbe = failure
                        diagnostic("host=${runCatching { java.net.URI(candidate.url).host }.getOrDefault("unknown")} ${failure.message}")
                    }
                }
                found
            }
            resolved ?: throw (fatal.get() ?: lastProbe ?: if(scriptError)
                MediaResolutionFailure("网页脚本", "页面报告语法错误，可能不兼容当前 WebView")
                else MediaResolutionFailure("媒体发现", "等待超时，未找到可用媒体（候选 ${seen.size}）"))
        } finally {
            polling?.cancel()
            web.stopLoading(); web.webViewClient = WebViewClient(); web.webChromeClient = WebChromeClient()
            web.destroy(); candidates.close()
        }
    }
    private suspend fun probe(request: PlaybackRequest): PlaybackRequest = suspendCancellableCoroutine { continuation ->
        val builder = Request.Builder().url(request.url).header("Range","bytes=0-1023")
        request.headers.forEach { (name,value) -> builder.header(name,value) }
        val call = AppHttp.client.newBuilder().callTimeout(5,TimeUnit.SECONDS).build().newCall(builder.build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val reason = when(e) {
                    is java.net.UnknownHostException -> "DNS解析失败"
                    is javax.net.ssl.SSLException -> "TLS连接失败"
                    is java.net.SocketTimeoutException -> "连接或读取超时"
                    else -> "连接失败或超时"
                }
                if(continuation.isActive) continuation.resumeWithException(MediaResolutionFailure("媒体探测",reason))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        if(!it.isSuccessful) throw MediaResolutionFailure("媒体探测","HTTP ${it.code}")
                        val body = it.body ?: throw MediaResolutionFailure("媒体探测","空响应")
                        val bytes = ByteArray(1024)
                        var count = 0
                        body.byteStream().use { stream ->
                            while(count < bytes.size) { val n = stream.read(bytes,count,bytes.size-count); if(n < 0) break; count += n }
                        }
                        val mime = MediaProbe.mime(bytes,count,body.contentType()?.toString().orEmpty().lowercase())
                            ?: throw MediaResolutionFailure("媒体探测","响应不是可识别的视频或播放清单")
                        request.copy(mimeType = mime)
                    }
                    if(continuation.isActive) continuation.resume(result)
                } catch(e: Exception) {
                    if(continuation.isActive) continuation.resumeWithException(if(e is MediaResolutionFailure) e else MediaResolutionFailure("媒体探测","读取失败"))
                }
            }
        })
    }
}
