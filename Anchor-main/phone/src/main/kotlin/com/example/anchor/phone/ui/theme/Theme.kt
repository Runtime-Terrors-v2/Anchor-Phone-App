package com.Anchor.watchguardian.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary       = DeepBlue,
    onPrimary     = White,
    background    = Background,
    surface       = CardBg,
    onSurface     = TextPrimary,
    error         = AlertRed
)

@Composable
fun AnchorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content     = content
    )
}
