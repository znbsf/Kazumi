package org.kazumi.tv.playback

import org.kazumi.tv.data.DanmakuComment

data class TimedDanmaku(val comment: DanmakuComment, val lane: Int) {
    val endMs get() = comment.timeMs + 7500
}

/** Sparse TV layout: six scrolling lanes, one top and one lower lane. No overlapping lane reuse. */
class DanmakuTimeline(comments: List<DanmakuComment>) {
    val scheduled: List<TimedDanmaku> = buildList {
        val freeAt = LongArray(8)
        for (comment in comments.sortedBy { it.timeMs }) {
            val lanes = when(comment.mode) { 5 -> 6..6; 4 -> 7..7; else -> 0..5 }
            val lane = lanes.firstOrNull { freeAt[it] <= comment.timeMs } ?: continue
            add(TimedDanmaku(comment, lane))
            freeAt[lane] = comment.timeMs + 7500
        }
    }
    fun visible(timeMs: Long): List<TimedDanmaku> {
        var low = 0; var high = scheduled.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (scheduled[middle].comment.timeMs <= timeMs - 7500) low = middle + 1 else high = middle
        }
        val result = ArrayList<TimedDanmaku>(8)
        while (low < scheduled.size && scheduled[low].comment.timeMs <= timeMs) result.add(scheduled[low++])
        return result
    }
}
