package com.Anchor.watchguardian.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary       = TextPrimary,   // buttons, selected items
    onPrimary     = White,
    background    = Background,
    surface       = CardBg,
    onSurface     = TextPrimary,
    error         = AlertRed
)

/**
 * App-wide MaterialTheme wrapper.
 * All Compose screens are wrapped in AnchorTheme via the NavHost in MainActivity.
 */
@Composable
fun AnchorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content     = content
    )
}
