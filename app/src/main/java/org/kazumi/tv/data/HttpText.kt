package org.kazumi.tv.data

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.net.URI
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object HttpText {
    private fun build(url: String, method: String, headers: Map<String,String>, body: String?): Request {
        val uri = URI(url)
        require(uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank()) { "网络地址无效" }
        val builder = Request.Builder().url(url).header("User-Agent", "KazumiTV/0.2 (Android TV)")
        headers.forEach { (key, value) -> builder.header(key, value) }
        val payload = if (method == "POST") (body ?: "").toRequestBody(headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value?.toMediaTypeOrNull()) else null
        builder.method(method, payload)
        return builder.build()
    }
    private fun read(response: Response, maxChars: Int): String = response.use {
            check(response.isSuccessful) { "服务返回 HTTP ${response.code}" }
            response.body!!.charStream().use { reader ->
                BoundedText.read(reader,maxChars)
            }
        }
    fun request(url: String, method: String = "GET", headers: Map<String, String> = emptyMap(), body: String? = null, maxChars: Int = 2_000_000): String =
        read(AppHttp.client.newCall(build(url,method,headers,body)).execute(),maxChars)

    suspend fun requestAsync(url: String, method: String = "GET", headers: Map<String,String> = emptyMap(), body: String? = null,
        maxChars: Int = 2_000_000): String = exchange(AppHttp.client,build(url,method,headers,body)) { read(it,maxChars) }

    /** Cancel the socket as well as the coroutine; response ownership stays inside the callback. */
    internal suspend fun <T> exchange(client: OkHttpClient, request: Request, consume: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
        val call=client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) { if(continuation.isActive)continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                if(!continuation.isActive) { response.close(); return }
                try { val result=response.use(consume); if(continuation.isActive)continuation.resume(result) }
                catch(e:Exception) { if(continuation.isActive)continuation.resumeWithException(e) }
            }
        })
    }
}
