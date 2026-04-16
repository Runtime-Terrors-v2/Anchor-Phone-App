package com.Anchor.watchguardian.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Anchor.watchguardian.data.UserSession
import com.Anchor.watchguardian.ui.theme.*
import com.Anchor.watchguardian.viewmodel.HomeViewModel

/**
 * Caregiver home screen — main dashboard.
 *
 * Mirrors HomePage.ets (HarmonyOS) with the same layout:
 *   - Header: "ANCHOR" title + user initials avatar
 *   - Watch status card (green = connected, red = disconnected)
 *   - Stats row: contact count, alert count this week, ON/OFF guardian status
 *   - MANAGE section: nav to Contacts and Alert History
 *   - Bottom: Refresh, Simulate Disconnect (debug), Sign Out
 *
 * State is collected from HomeViewModel StateFlows.
 */
@Composable
fun HomeScreen(
    viewModel:            HomeViewModel,
    onNavigateContacts:   () -> Unit,
    onNavigateAlerts:     () -> Unit,
    onNavigateGeofence:   () -> Unit,
    onSignOut:            () -> Unit
) {
    val context        = LocalContext.current
    val watchConnected by viewModel.watchConnected.collectAsState()
    val watchName      by viewModel.watchName.collectAsState()
    val alertHistory   by viewModel.alertHistory.collectAsState()

    // Initials from OpenID — same logic as getInitials() in HomePage.ets
    val initials = UserSession.getOpenID(context).let { id ->
        if (id.length >= 2) id.substring(0, 2).uppercase() else "HJ"
    }

    // "Last seen" text — mirrors getLastSeenText() in HomePage.ets
    val lastSeenText = when {
        watchConnected -> "Last seen: just now"
        alertHistory.isNotEmpty() -> {
            val diffMs = System.currentTimeMillis() - alertHistory.first().timestamp
            val mins   = (diffMs / 60_000).toInt()
            if (mins < 60) "Offline · ${mins}m ago" else "Offline · ${mins / 60}h ago"
        }
        else -> "Offline · alerts sent to contacts"
    }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .background(White),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Header ---
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("ANCHOR", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(36.dp)
                    .background(BlueLight, CircleShape)
            ) {
                Text(initials, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0C447C))
            }
        }

        // --- Watch status card ---
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(
                    if (watchConnected) SafeCardBg else AlertCardBg,
                    RoundedCornerShape(12.dp)
                )
                .border(
                    0.5.dp,
                    if (watchConnected) SafeCardBorder else AlertCardBorder,
                    RoundedCornerShape(12.dp)
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(if (watchConnected) SafeGreen else AlertRed, CircleShape)
                )
                Text(
                    text       = if (watchConnected) "Watch connected" else "Watch disconnected",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color      = if (watchConnected) Color(0xFF0F6E56) else Color(0xFFA32D2D)
                )
            }
            Text(
                text     = watchName.ifBlank { "Huawei Watch Ultimate" },
                fontSize = 13.sp,
                color    = TextSecond
            )
            Text(text = lastSeenText, fontSize = 12.sp, color = TextMuted)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- Stats row ---
        Row(
            modifier              = Modifier.fillMaxWidth(0.9f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                label      = "Contacts",
                value      = viewModel.getContactCount().toString(),
                sub        = "priority",
                modifier   = Modifier.weight(1f)
            )
            StatCard(
                label      = "Alerts",
                value      = viewModel.getAlertCountThisWeek().toString(),
                sub        = "this week",
                modifier   = Modifier.weight(1f)
            )
            StatCard(
                label      = "Status",
                value      = if (watchConnected) "ON" else "OFF",
                sub        = "guardian",
                modifier   = Modifier.weight(1f),
                valueColor = if (watchConnected) SafeGreen else AlertRed
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- MANAGE section label ---
        Text(
            text          = "MANAGE",
            fontSize      = 12.sp,
            fontWeight    = FontWeight.Medium,
            color         = TextMuted,
            letterSpacing = 0.5.sp,
            modifier      = Modifier
                .fillMaxWidth(0.9f)
                .padding(bottom = 8.dp)
        )

        // --- Nav cards ---
        Column(
            modifier            = Modifier.fillMaxWidth(0.9f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NavCard(
                emoji    = "\uD83D\uDC65", // 👥
                tint     = PurpleLight,
                title    = "Priority contacts",
                subtitle = "${viewModel.getContactCount()} contacts added",
                onClick  = onNavigateContacts
            )
            NavCard(
                emoji    = "\uD83D\uDD14", // 🔔
                tint     = AmberLight,
                title    = "Alert history",
                subtitle = "Last alert: ${if (alertHistory.isEmpty()) "none" else "recently"}",
                badge    = viewModel.getAlertCountThisWeek().takeIf { it > 0 }?.toString(),
                onClick  = onNavigateAlerts
            )
            NavCard(
                emoji    = "\uD83D\uDCCD", // 📍
                tint     = Color(0xFFE8F5E9),
                title    = "Geofence monitor",
                subtitle = "GPS drift detection on this phone",
                onClick  = onNavigateGeofence
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // --- Bottom action bar ---
        Row(
            modifier              = Modifier
                .fillMaxWidth(0.9f)
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { viewModel.refreshWatchStatus() },
                shape   = RoundedCornerShape(8.dp)
            ) {
                Text("Refresh", fontSize = 13.sp, color = TextSecond)
            }

            // DEBUG ONLY — remove before submission; mirrors "Simulate disconnect" in HomePage.ets
            Button(
                onClick = { viewModel.simulateDisconnect() },
                shape   = RoundedCornerShape(22.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = AlertRed)
            ) {
                Text("Simulate disconnect", fontSize = 12.sp)
            }

            TextButton(onClick = onSignOut) {
                Text("Sign out", fontSize = 13.sp, color = AlertRed)
            }
        }
    }
}

// --- Shared sub-composables ---

@Composable
private fun StatCard(
    label:      String,
    value:      String,
    sub:        String,
    modifier:   Modifier = Modifier,
    valueColor: Color    = TextPrimary
) {
    Column(
        modifier            = modifier
            .background(Background, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, fontSize = 11.sp, color = TextSecond)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = valueColor)
        Text(sub,   fontSize = 11.sp, color = TextMuted)
    }
}

@Composable
private fun NavCard(
    emoji:   String,
    tint:    Color,
    title:   String,
    subtitle: String,
    badge:   String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(12.dp))
            .border(0.5.dp, Color(0xFFEEEEEE), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Emoji icon in a tinted box
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .size(36.dp)
                .background(tint, RoundedCornerShape(8.dp))
        ) {
            Text(emoji, fontSize = 16.sp)
        }

        // Title + optional badge + subtitle
        Column(
            modifier            = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                if (badge != null) {
                    Text(
                        text     = badge,
                        fontSize = 10.sp,
                        color    = White,
                        modifier = Modifier
                            .background(AlertRed, RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(subtitle, fontSize = 12.sp, color = TextSecond)
        }

        Text("\u203A", fontSize = 18.sp, color = Color(0xFFCCCCCC)) // ›
    }
}
