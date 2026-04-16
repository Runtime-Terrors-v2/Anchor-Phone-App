package com.Anchor.watchguardian.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

    val initials = UserSession.getOpenID(context).let { id ->
        if (id.length >= 2) id.substring(0, 2).uppercase() else "HJ"
    }

    val lastSeenText = when {
        watchConnected -> "Connected right now"
        alertHistory.isNotEmpty() -> {
            val diffMs = System.currentTimeMillis() - alertHistory.first().timestamp
            val mins   = (diffMs / 60_000).toInt()
            if (mins < 60) "Last seen ${mins}m ago" else "Last seen ${mins / 60}h ago"
        }
        else -> "Not connected"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Header ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(White)
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Text(
                text       = "ANCHOR",
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary,
                letterSpacing = 1.sp,
                modifier   = Modifier.align(Alignment.CenterStart)
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(42.dp)
                    .background(BlueLight, CircleShape)
                    .align(Alignment.CenterEnd)
            ) {
                Text(
                    text       = initials,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = DeepBlue
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- Watch status card ---
        val connected = watchConnected
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(
                    if (connected) SafeCardBg else AlertCardBg,
                    RoundedCornerShape(20.dp)
                )
                .border(
                    1.5.dp,
                    if (connected) SafeCardBorder else AlertCardBorder,
                    RoundedCornerShape(20.dp)
                )
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                if (connected) SafeGreen else AlertRed,
                                CircleShape
                            )
                    )
                    Text(
                        text       = if (connected) "Watch Connected" else "Watch Disconnected",
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = if (connected) Color(0xFF0F6E56) else Color(0xFFB91C1C)
                    )
                }
                Text(
                    text     = watchName.ifBlank { "Huawei Watch Ultimate" },
                    fontSize = 15.sp,
                    color    = TextSecond
                )
                Text(
                    text     = lastSeenText,
                    fontSize = 14.sp,
                    color    = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- Quick stats ---
        Row(
            modifier              = Modifier
                .fillMaxWidth(0.9f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickStat(
                value    = viewModel.getContactCount().toString(),
                label    = "Contacts",
                modifier = Modifier.weight(1f)
            )
            QuickStat(
                value    = viewModel.getAlertCountThisWeek().toString(),
                label    = "Alerts this week",
                modifier = Modifier.weight(1f)
            )
            QuickStat(
                value      = if (watchConnected) "ON" else "OFF",
                label      = "Guardian",
                valueColor = if (watchConnected) SafeGreen else AlertRed,
                modifier   = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // --- Section label ---
        Text(
            text          = "MANAGE",
            fontSize      = 12.sp,
            fontWeight    = FontWeight.SemiBold,
            color         = TextMuted,
            letterSpacing = 1.5.sp,
            modifier      = Modifier
                .fillMaxWidth(0.9f)
                .padding(bottom = 10.dp)
        )

        // --- Nav cards ---
        Column(
            modifier            = Modifier.fillMaxWidth(0.9f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NavCard(
                emoji    = "👥",
                tint     = PurpleLight,
                title    = "Priority Contacts",
                subtitle = "${viewModel.getContactCount()} contacts will be alerted",
                onClick  = onNavigateContacts
            )
            NavCard(
                emoji    = "🔔",
                tint     = AmberLight,
                title    = "Alert History",
                subtitle = if (alertHistory.isEmpty()) "No alerts yet"
                           else "${viewModel.getAlertCountThisWeek()} alert(s) this week",
                badge    = viewModel.getAlertCountThisWeek().takeIf { it > 0 }?.toString(),
                onClick  = onNavigateAlerts
            )
            NavCard(
                emoji    = "📍",
                tint     = SafeCardBg,
                title    = "Geofence Monitor",
                subtitle = "GPS drift detection · Set home anchor",
                onClick  = onNavigateGeofence
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // --- Actions row ---
        Row(
            modifier              = Modifier
                .fillMaxWidth(0.9f)
                .padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick  = { viewModel.refreshWatchStatus() },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Text("Refresh", fontSize = 15.sp, color = TextSecond)
            }

            // DEBUG
            OutlinedButton(
                onClick  = { viewModel.simulateDisconnect() },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = OutlinedButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
                border   = ButtonDefaults.outlinedButtonBorder
            ) {
                Text("Simulate", fontSize = 13.sp, color = AlertRed)
            }

            TextButton(
                onClick  = onSignOut,
                modifier = Modifier.height(52.dp)
            ) {
                Text("Sign out", fontSize = 15.sp, color = AlertRed)
            }
        }
    }
}

// --- Sub-composables ---

@Composable
private fun QuickStat(
    value:      String,
    label:      String,
    modifier:   Modifier = Modifier,
    valueColor: Color    = TextPrimary
) {
    Column(
        modifier            = modifier
            .background(White, RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text       = value,
            fontSize   = 26.sp,
            fontWeight = FontWeight.Bold,
            color      = valueColor
        )
        Text(
            text     = label,
            fontSize = 12.sp,
            color    = TextMuted
        )
    }
}

@Composable
private fun NavCard(
    emoji:    String,
    tint:     Color,
    title:    String,
    subtitle: String,
    badge:    String? = null,
    onClick:  () -> Unit
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .background(White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(18.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .size(48.dp)
                .background(tint, RoundedCornerShape(12.dp))
        ) {
            Text(emoji, fontSize = 22.sp)
        }

        Column(
            modifier            = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text       = title,
                    fontSize   = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = TextPrimary
                )
                if (badge != null) {
                    Text(
                        text     = badge,
                        fontSize = 11.sp,
                        color    = White,
                        modifier = Modifier
                            .background(AlertRed, RoundedCornerShape(10.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                text     = subtitle,
                fontSize = 14.sp,
                color    = TextSecond
            )
        }

        Text("›", fontSize = 22.sp, color = Color(0xFFD1D5DB))
    }
}
