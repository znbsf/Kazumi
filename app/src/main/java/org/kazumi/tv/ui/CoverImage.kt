package org.kazumi.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.kazumi.tv.data.NetworkSettings

@Composable
fun CoverImage(model: String, contentDescription: String?, modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop, allowRetry: Boolean = false, alignment: Alignment = Alignment.Center, emptyLabel: String = "暂无封面") {
    val context=LocalContext.current
    val revision by NetworkSettings.catalogRevision.collectAsState()
    val mirrored=remember(revision) { NetworkSettings.catalogMirror }
    var retry by remember(model,mirrored) { mutableIntStateOf(0) }
    var failed by remember(model,mirrored,retry) { mutableStateOf(false) }
    var loaded by remember(model,mirrored,retry) { mutableStateOf(false) }
    val request=remember(model,mirrored,retry) {
        ImageRequest.Builder(context).data(model)
            .memoryCacheKey("${NetworkSettings.catalogMirror}:$model:$retry")
            .diskCacheKey("${NetworkSettings.catalogMirror}:$model").build()
    }
    Box(modifier.background(KazumiColors.surface),contentAlignment=Alignment.Center) {
        if(model.isNotBlank()) AsyncImage(request,contentDescription,Modifier.fillMaxSize(),contentScale=contentScale,alignment=alignment,
            onSuccess={ loaded=true; failed=false },onError={ failed=true })
        if(!loaded) Column(Modifier.padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(if(model.isBlank()) emptyLabel else if(failed) "封面加载失败" else "封面加载中…",style=KazumiType.caption,color=KazumiColors.muted)
            if(failed && allowRetry)PlayerAction("重试封面") { retry++ }
        }
    }
}
