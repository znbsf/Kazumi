@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package org.kazumi.tv.ui

import android.annotation.SuppressLint
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import org.kazumi.tv.rules.SourceRule

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VerificationScreen(rule: SourceRule, startUrl: String = rule.baseUrl, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFF111713)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${rule.name} · 网页验证")
        Button(onClick = { CookieManager.getInstance().flush(); onDone() }) { Text("完成验证，重试来源") }
        AndroidView(factory = { WebView(it).apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            settings.allowFileAccess = false; settings.allowContentAccess = false; settings.userAgentString = rule.userAgent
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = request.url.scheme !in listOf("http", "https")
            }
            loadUrl(SourceRule.httpUrl(startUrl), mapOf("Referer" to rule.referer))
        } }, modifier = Modifier.fillMaxWidth().weight(1f), onRelease = { it.stopLoading(); it.destroy() })
    }
}
