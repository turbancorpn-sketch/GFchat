package com.gfchat.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Rose = Color(0xFFE91E63)
val RoseDark = Color(0xFFAD1457)
val RoseSoft = Color(0xFFFCE4EC)

private val Light = lightColorScheme(
    primary = Rose,
    onPrimary = Color.White,
    primaryContainer = RoseSoft,
    onPrimaryContainer = RoseDark,
    secondaryContainer = Color(0xFFF3E5F5),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF80AB),
    onPrimary = Color(0xFF3B0016),
    primaryContainer = RoseDark,
    onPrimaryContainer = RoseSoft,
)

@Composable
fun GFchatTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
