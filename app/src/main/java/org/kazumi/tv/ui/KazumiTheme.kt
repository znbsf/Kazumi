package org.kazumi.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** One type scale shared by browsing, detail, settings and playback controls. */
object KazumiType {
    val heading = TextStyle(fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium)
    val title = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val body = TextStyle(fontSize = 15.sp, lineHeight = 21.sp)
    val caption = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val control = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
}

object KazumiColors {
    val accent = Color(0xFFB8E8A4)
    val background = Color(0xFF101611)
    val surface = Color(0xFF1C261F)
    val selected = Color(0xFF354F38)
    val text = Color(0xFFE8EEE5)
    val muted = Color(0xFFB6C2B3)
}

@Composable
fun KazumiTheme(oled: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = KazumiColors.accent, onPrimary = Color(0xFF173513),
        background = if (oled) Color.Black else KazumiColors.background, surface = KazumiColors.surface,
        surfaceVariant = KazumiColors.surface, onSurfaceVariant = KazumiColors.muted,
        secondaryContainer = KazumiColors.selected, onSurface = KazumiColors.text, onBackground = KazumiColors.text),
        typography = Typography(headlineLarge = KazumiType.heading, headlineMedium = KazumiType.heading,
            headlineSmall = KazumiType.heading, titleLarge = KazumiType.title, titleMedium = KazumiType.title,
            titleSmall = KazumiType.control, bodyLarge = KazumiType.body, bodyMedium = KazumiType.body,
            bodySmall = KazumiType.caption, labelLarge = KazumiType.control, labelMedium = KazumiType.control,
            labelSmall = KazumiType.caption)) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.tv.material3.LocalContentColor provides KazumiColors.text) { content() }
    }
}
