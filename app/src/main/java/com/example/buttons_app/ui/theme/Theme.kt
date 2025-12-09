package com.example.buttons_app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    background = White,
    surface = White,
    onBackground = Black,
    onSurface = Black,
    primary = Red,
    secondary = Turquoise
)

@Composable
fun ButtonsappTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
