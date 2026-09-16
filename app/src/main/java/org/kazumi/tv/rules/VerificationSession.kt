package org.kazumi.tv.rules

import android.annotation.SuppressLint
import android.webkit.*
import kotlinx.coroutines.*
import org.json.*
import kotlin.coroutines.resume

/** UI owns the WebView and cancels this loop before destroying it. No credentials or page text are logged. */
object VerificationSession {
    @SuppressLint("SetJavaScriptEnabled")
    fun configure(web:WebView,rule:SourceRule) {
        web.settings.javaScriptEnabled=true
        web.settings.domStorageEnabled=true
        web.settings.allowFileAccess=false
        web.settings.allowContentAccess=false
        web.settings.userAgentString=rule.userAgent
        web.settings.mixedContentMode=WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    }
    suspend fun evaluate(web:WebView,script:String):String = suspendCancellableCoroutine { continuation ->
        web.evaluateJavascript(script) { if(continuation.isActive)continuation.resume(it?:"null") }
    }
    suspend fun await(web:WebView,rule:SourceRule,progress:VerificationProgress=VerificationProgress(),onSnapshot:(JSONObject)->Unit={}) {
        while(currentCoroutineContext().isActive) {
            val raw=evaluate(web,VerificationScript.poll(rule))
            val snapshot=runCatching { JSONObject(JSONTokener(raw).nextValue().toString()) }.getOrNull()
            if(snapshot!=null) {
                onSnapshot(snapshot)
                if(progress.observe(snapshot)) { CookieManager.getInstance().flush(); return }
            }
            delay(400)
        }
    }
}
