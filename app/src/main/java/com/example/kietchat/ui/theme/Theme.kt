package com.example.kietchat.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BlueColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color.White,
    secondary = Color(0xFF90CAF9),
    onSecondary = Color.Black
)

@Composable
fun KIETChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BlueColorScheme,
        typography = Typography(),
        content = content
    )
}
