package org.kazumi.tv.ui

import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import androidx.media3.ui.PlayerView
import org.kazumi.tv.R
import org.kazumi.tv.data.TvPreferences

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun createPlaybackView(context: Context): PlayerView {
    val texture = TvPreferences(context).videoOutput.usesTexture(Build.DEVICE, Build.VERSION.SDK_INT)
    return (if (texture) LayoutInflater.from(context).inflate(R.layout.player_texture,null) as PlayerView
        else PlayerView(context)).apply { useController = false }
}
