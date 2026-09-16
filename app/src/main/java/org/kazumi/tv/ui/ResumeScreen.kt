package org.kazumi.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.kazumi.tv.data.HistoryEntry
import org.kazumi.tv.rules.Episode

@Composable
fun ResumeScreen(entry: HistoryEntry, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        if(entry.kind==org.kazumi.tv.data.HistoryKind.OFFLINE) OfflinePlayback(entry.key.removePrefix("offline|"),onClose)
        else PlaybackSessionScreen(entry.subject, entry.origin?.rule ?: entry.key.substringBefore('|'),
            Episode(entry.episode.substringAfterLast(" · "), entry.key.substringAfter('|', "")),
            initialOrigin = entry.origin, onClose = onClose)
    }
}
