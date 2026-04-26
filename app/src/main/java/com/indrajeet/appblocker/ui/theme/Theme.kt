package com.indrajeet.appblocker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Slate,
    secondary = Rust,
    tertiary = Sky,
    background = Sand,
    surface = Sand,
    onPrimary = Sand,
    onSecondary = Sand,
    onTertiary = Ink,
    onBackground = Ink,
    onSurface = Ink
)

@Composable
fun AppBlockerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
