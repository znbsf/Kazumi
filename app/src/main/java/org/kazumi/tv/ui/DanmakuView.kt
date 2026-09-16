package org.kazumi.tv.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.media3.common.Player
import org.kazumi.tv.playback.DanmakuTimeline

class DanmakuView(context: Context) : View(context) {
    var timeline = DanmakuTimeline(emptyList())
    var player: Player? = null
    var offsetMs = 0L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        setShadowLayer(3f, 1f, 1f, android.graphics.Color.BLACK)
    }
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO; isFocusable = false; isClickable = false }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = player ?: return
        val time = current.currentPosition - offsetMs
        paint.textSize = 18f * resources.displayMetrics.scaledDensity
        for (item in timeline.visible(time)) {
            val textWidth = paint.measureText(item.comment.text)
            val progress = (time - item.comment.timeMs) / 7500f
            val x = if (item.comment.mode == 1) width - (width + textWidth) * progress else (width - textWidth) / 2f
            val y = when(item.lane) { 6 -> height * .075f; 7 -> height * .72f; else -> height * .14f + item.lane * paint.textSize * 1.5f }
            paint.color = item.comment.color
            canvas.drawText(item.comment.text, x, y, paint)
        }
        if (isAttachedToWindow) postInvalidateDelayed(if (current.isPlaying) 33 else 250)
    }
}
