package com.Anchor.watchguardian.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Anchor.watchguardian.data.model.AlertEvent
import com.Anchor.watchguardian.ui.theme.*
import com.Anchor.watchguardian.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Alert history screen — log of all watch-disconnect events.
 *
 * Mirrors AlertHistoryPage.ets (HarmonyOS) with:
 *   - Empty state when no alerts
 *   - Summary banner with total count
 *   - Scrollable list of alert events (index pill, watch name, date, time-ago)
 *   - Clear All button
 *
 * Uses HomeViewModel since alert history is owned there (same source as HomeScreen stats).
 */
@Composable
fun AlertHistoryScreen(
    viewModel: HomeViewModel,
    onBack:    () -> Unit
) {
    val alertHistory by viewModel.alertHistory.collectAsState()

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .background(Background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Header ---
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .border(width = 0.5.dp, color = Color(0xFFEEEEEE))
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onBack) {
                    Text("\u2190", fontSize = 20.sp, color = TextPrimary) // ←
                }
                Text("Alert history", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            }
            if (alertHistory.isNotEmpty()) {
                TextButton(onClick = { viewModel.clearAlertHistory() }) {
                    Text("Clear all", fontSize = 13.sp, color = AlertRed)
                }
            }
        }

        // --- Empty state ---
        if (alertHistory.isEmpty()) {
            Column(
                modifier            = Modifier.padding(top = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("No alerts yet", fontSize = 16.sp, color = TextSecond)
                Text(
                    text     = "Disconnect events will appear here\nwhen your watch goes offline.",
                    fontSize = 13.sp,
                    color    = TextMuted
                )
            }
            return@Column
        }

        // --- Summary banner ---
        Row(
            modifier              = Modifier
                .fillMaxWidth(0.9f)
                .padding(top = 16.dp, bottom = 8.dp)
                .background(Color(0xFFFFF5F5), RoundedCornerShape(10.dp))
                .border(0.5.dp, Color(0xFFF7C1C1), RoundedCornerShape(10.dp))
                .padding(14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text       = alertHistory.size.toString(),
                fontSize   = 22.sp,
                fontWeight = FontWeight.Medium,
                color      = AlertRed
            )
            Text(
                text     = if (alertHistory.size == 1) "disconnect event recorded" else "disconnect events recorded",
                fontSize = 13.sp,
                color    = TextSecond
            )
        }

        // --- Alert list ---
        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding      = PaddingValues(bottom = 40.dp)
        ) {
            itemsIndexed(alertHistory) { index, alert ->
                AlertRow(index = index, alert = alert)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AlertRow(index: Int, alert: AlertEvent) {
    Row(
        modifier              = Modifier
            .fillMaxWidth(0.9f)
            .background(White, RoundedCornerShape(12.dp))
            .border(0.5.dp, Color(0xFFEEEEEE), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Index pill (1-based, same as ArkTS version)
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .size(28.dp)
                .background(Color(0xFFFCEBEB), CircleShape)
        ) {
            Text(
                text       = (index + 1).toString(),
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium,
                color      = Color(0xFFA32D2D)
            )
        }

        // Alert info
        Column(
            modifier            = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text       = alert.watchName.ifBlank { "Huawei Watch" },
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color      = TextPrimary
                )
                Text(
                    text     = "offline",
                    fontSize = 10.sp,
                    color    = Color(0xFFA32D2D),
                    modifier = Modifier
                        .background(Color(0xFFFCEBEB), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Text(formatDate(alert.timestamp), fontSize = 12.sp, color = TextSecond)
        }

        // Time ago
        Text(timeAgo(alert.timestamp), fontSize = 11.sp, color = TextMuted)
    }
}

/** Format timestamp as "DD/MM at HH:mm" — mirrors formatDate() in AlertHistoryPage.ets. */
private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd/MM 'at' HH:mm", Locale.getDefault()).format(Date(timestamp))

/** Human-readable elapsed time — mirrors getTimeAgo() in AlertHistoryPage.ets. */
private fun timeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val mins = (diff / 60_000).toInt()
    return when {
        mins < 1  -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 1440 -> "${mins / 60}h ago"
        else       -> "${mins / 1440}d ago"
    }
}
