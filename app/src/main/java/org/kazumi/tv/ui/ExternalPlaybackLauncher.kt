package org.kazumi.tv.ui

import android.content.Intent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun rememberExternalLauncher(onResult:(ActivityResult)->Unit):(Intent)->Unit {
    val owner=LocalActivityResultRegistryOwner.current ?: (LocalLifecycleOwner.current as? ActivityResultRegistryOwner)
    val key=rememberSaveable { "external-"+java.util.UUID.randomUUID() }
    val callback by rememberUpdatedState(onResult)
    val holder=remember(owner,key) { arrayOfNulls<ActivityResultLauncher<Intent>>(1) }
    DisposableEffect(owner,key) {
        holder[0]=owner?.activityResultRegistry?.register(key,ActivityResultContracts.StartActivityForResult()) { callback(it) }
        onDispose { holder[0]?.unregister(); holder[0]=null }
    }
    return { intent -> checkNotNull(holder[0]) { "外部播放入口暂不可用" }.launch(intent) }
}
