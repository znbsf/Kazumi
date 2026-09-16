package org.kazumi.tv.rules

import android.content.Context
import android.webkit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One automatic challenge at a time on low-memory TVs. Caller retries the exact original request once. */
object AutomaticVerification {
    private val lock=Mutex()
    fun supported(rule:SourceRule):Boolean = rule.json.optJSONObject("antiCrawlerConfig")?.let {
        it.optBoolean("enabled")&&it.optInt("captchaType") in 2..3
    }==true
    suspend fun run(context:Context,rule:SourceRule,url:String,method:String="GET",body:String?=null):Boolean {
        if(!supported(rule))return false
        return lock.withLock {
            withContext(Dispatchers.Main) {
                val web=WebView(context)
                var failed=false
                try {
                    VerificationSession.configure(web,rule)
                    web.webViewClient=object:WebViewClient() {
                        override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest)=request.url.scheme !in listOf("http","https")
                        override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError) { if(request.isForMainFrame)failed=true }
                        override fun onReceivedHttpError(view:WebView,request:WebResourceRequest,response:WebResourceResponse) { if(request.isForMainFrame&&response.statusCode !in listOf(403,429))failed=true }
                    }
                    if(method=="POST")web.postUrl(SourceRule.httpUrl(url),body.orEmpty().toByteArray(Charsets.UTF_8))
                    else web.loadUrl(SourceRule.httpUrl(url),mapOf("Referer" to rule.referer))
                    withTimeoutOrNull(20000) { VerificationSession.await(web,rule); !failed } ?: false
                } finally { web.stopLoading(); CookieManager.getInstance().flush(); web.destroy() }
            }
        }
    }
}
