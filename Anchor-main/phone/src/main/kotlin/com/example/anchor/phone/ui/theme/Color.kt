package com.Anchor.watchguardian.ui.theme

import androidx.compose.ui.graphics.Color

// --- Base UI colours (light phone theme) ---
val White       = Color(0xFFFFFFFF)
val Background  = Color(0xFFF8F8F8) // page background
val CardBg      = Color(0xFFFFFFFF) // card surface
val TextPrimary = Color(0xFF1A1A1A)
val TextSecond  = Color(0xFF888888)
val TextMuted   = Color(0xFFAAAAAA)

// --- Status colours — same semantic palette as the watch ArkTS dark theme ---
// Safe: #4ADE80 on watch (dark bg), #1DAB5F on phone (light bg) for enough contrast
val SafeGreen   = Color(0xFF1DAB5F)
val DriftAmber  = Color(0xFFFBBF24)
val AlertRed    = Color(0xFFE84026)

// HMS Huawei red — used for the "Sign in with Huawei ID" button
val HmsRed      = Color(0xFFCC0000)

// --- Card tint variants ---
val SafeCardBg      = Color(0xFFE1F5EE)
val SafeCardBorder  = Color(0xFF9FE1CB)
val AlertCardBg     = Color(0xFFFCEBEB)
val AlertCardBorder = Color(0xFFF7C1C1)
val PurpleLight     = Color(0xFFEEEDFE) // contacts card tint
val BlueLight       = Color(0xFFE6F1FB) // info banner + avatar tint
val AmberLight      = Color(0xFFFAEEDA) // alert-history card tint
