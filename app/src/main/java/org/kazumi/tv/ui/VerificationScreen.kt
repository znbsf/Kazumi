@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package org.kazumi.tv.ui

import android.annotation.SuppressLint
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.*
import org.kazumi.tv.rules.VerificationScript
import org.kazumi.tv.rules.VerificationSession
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import org.kazumi.tv.rules.SourceRule

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VerificationScreen(rule: SourceRule, startUrl: String = rule.baseUrl, challenge:org.kazumi.tv.rules.SourceVerificationRequired?=null, onDone: () -> Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val currentDone by rememberUpdatedState(onDone)
    val pointerFocus=remember { FocusRequester() }
    var view by remember { mutableStateOf<WebView?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var code by remember { mutableStateOf("") }
    var image by remember { mutableStateOf("") }
    var submittedImage by remember { mutableStateOf("") }
    var inputNotice by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("正在检测验证页面…") }
    var loadFailed by remember { mutableStateOf(false) }
    var delivered by remember { mutableStateOf(false) }
    val progress=remember { org.kazumi.tv.rules.VerificationProgress() }
    val config=rule.json.optJSONObject("antiCrawlerConfig")
    val imageMode=config?.optBoolean("enabled")==true && config.optInt("captchaType",1)==1
    LaunchedEffect(view,generation) {
        val web=view ?: return@LaunchedEffect
        try {
            withTimeout(60000) {
                VerificationSession.await(web,rule,progress) { snapshot ->
                    val nextImage=snapshot.optString("image")
                    if(submittedImage.isNotEmpty()&&nextImage.isNotEmpty()&&nextImage!=submittedImage&&snapshot.optBoolean("challenge")) {
                        code="";submittedImage="";inputNotice="验证码已更新，请重新输入"
                    }
                    image=nextImage
                    status=when {
                        loadFailed -> "验证页面加载失败，请重试"
                        snapshot.optBoolean("failed") -> "验证规则执行失败，可在网页中操作或重试"
                        inputNotice.isNotBlank() -> inputNotice
                        imageMode && image.isNotBlank() -> "输入图中验证码后提交；通过后会自动继续"
                        snapshot.optBoolean("acted") -> "已执行验证操作，正在确认结果…"
                        else -> "正在检测验证页面，可使用网页完成验证"
                    }
                }
            }
            if(!loadFailed&&!delivered) { delivered=true; currentDone() }
        } catch(_:TimeoutCancellationException) { status="验证尚未通过，可继续操作网页后重新检测，或重新加载" }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF111713)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${rule.name} · 网页验证")
        Text(status)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Button(onClick = { generation++ }) { Text("重新检测") }
            Button(onClick = {
                loadFailed=false;image="";inputNotice="";submittedImage=""
                if(challenge?.method=="POST")view?.postUrl(SourceRule.httpUrl(startUrl),challenge.body.orEmpty().toByteArray(Charsets.UTF_8))
                else view?.loadUrl(SourceRule.httpUrl(startUrl),mapOf("Referer" to rule.referer))
                generation++
            }) { Text("重新加载") }
            Button(onClick = { (view as? VerificationWebView)?.pointerEnabled=true;view?.requestFocus() },modifier=Modifier.focusRequester(pointerFocus)) { Text("操作网页") }
            if(imageMode) {
                if(image.isNotBlank()) {
                    val model=remember(image) {
                        if(image.startsWith("data:image/")) runCatching { android.util.Base64.decode(image.substringAfter(','),android.util.Base64.DEFAULT) }.getOrNull()
                        else runCatching { coil.request.ImageRequest.Builder(context).data(SourceRule.httpUrl(image))
                            .addHeader("User-Agent",rule.userAgent).addHeader("Referer",startUrl)
                            .addHeader("Cookie",CookieManager.getInstance().getCookie(image).orEmpty()).build() }.getOrNull()
                    }
                    coil.compose.AsyncImage(model,contentDescription="验证码图片",modifier=Modifier.size(140.dp,52.dp))
                }
                TvTextInput(code,{ code=it.take(32);inputNotice="" },modifier=Modifier.width(180.dp))
                Button(enabled=code.isNotBlank(),onClick={ scope.launch {
                    val web=view ?: return@launch
                    submittedImage=image;inputNotice=""
                    val result=VerificationSession.evaluate(web,VerificationScript.submit(rule,code))
                    if(result=="true") { progress.markAction();status="已提交，正在确认验证结果…";generation++ }
                    else { inputNotice="未找到验证码输入框或提交按钮，请重新加载";status=inputNotice;submittedImage="" }
                } }) { Text("提交验证码") }
            }
        }
        Text("网页操作：方向键移动 · 确定点击 · 长按确定拖动，再按确定松开 · 返回退出光标",style=KazumiType.caption)
        AndroidView(factory = { VerificationWebView(it).apply {
            onExitPointer={ pointerFocus.requestFocus() }
            VerificationSession.configure(this,rule)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = request.url.scheme !in listOf("http", "https")
                override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError) { if(request.isForMainFrame)loadFailed=true }
                override fun onReceivedHttpError(view:WebView,request:WebResourceRequest,response:WebResourceResponse) { if(request.isForMainFrame&&response.statusCode !in listOf(403,429))loadFailed=true }
            }
            if(challenge?.method=="POST")postUrl(SourceRule.httpUrl(startUrl),challenge.body.orEmpty().toByteArray(Charsets.UTF_8))
            else loadUrl(SourceRule.httpUrl(startUrl), mapOf("Referer" to rule.referer))
            view=this
        } }, modifier = Modifier.fillMaxWidth().weight(1f), onRelease = { view=null; it.stopLoading(); CookieManager.getInstance().flush(); it.destroy() })
    }
}
