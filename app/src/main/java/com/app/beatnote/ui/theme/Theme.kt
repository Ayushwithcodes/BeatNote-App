package com.app.beatnote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = White,
    secondary = SubtleGrey,
    background = Black,
    surface = CharcoalDark,
    onBackground = White,
    onSurface = White,
)

@Composable
fun BeatNoteTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
