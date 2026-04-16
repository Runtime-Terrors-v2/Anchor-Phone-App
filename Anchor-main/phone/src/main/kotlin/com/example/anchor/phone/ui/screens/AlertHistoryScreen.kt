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
                .background(White)
                .border(width = 0.5.dp, color = Color(0xFFE5E7EB))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = onBack) {
                    Text("←", fontSize = 22.sp, color = TextPrimary)
                }
                Text(
                    text       = "Alert History",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = TextPrimary
                )
            }
            if (alertHistory.isNotEmpty()) {
                TextButton(onClick = { viewModel.clearAlertHistory() }) {
                    Text("Clear all", fontSize = 15.sp, color = AlertRed)
                }
            }
        }

        // --- Empty state ---
        if (alertHistory.isEmpty()) {
            Column(
                modifier            = Modifier
                    .fillMaxSize()
                    .padding(top = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("🔕", fontSize = 52.sp)
                Text(
                    text       = "No alerts yet",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color      = TextSecond
                )
                Text(
                    text     = "Watch disconnect events\nwill appear here.",
                    fontSize = 15.sp,
                    color    = TextMuted
                )
            }
            return@Column
        }

        // --- Summary banner ---
        Row(
            modifier              = Modifier
                .fillMaxWidth(0.9f)
                .padding(top = 16.dp, bottom = 4.dp)
                .background(AlertCardBg, RoundedCornerShape(14.dp))
                .border(1.dp, AlertCardBorder, RoundedCornerShape(14.dp))
                .padding(18.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text       = alertHistory.size.toString(),
                fontSize   = 32.sp,
                fontWeight = FontWeight.Bold,
                color      = AlertRed
            )
            Text(
                text     = if (alertHistory.size == 1) "disconnect event recorded"
                           else "disconnect events recorded",
                fontSize = 15.sp,
                color    = TextSecond
            )
        }

        // --- Alert list ---
        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding      = PaddingValues(top = 12.dp, bottom = 48.dp)
        ) {
            itemsIndexed(alertHistory) { index, alert ->
                AlertRow(index = index, alert = alert)
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun AlertRow(index: Int, alert: AlertEvent) {
    Row(
        modifier              = Modifier
            .fillMaxWidth(0.9f)
            .background(White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Index badge
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .size(36.dp)
                .background(AlertCardBg, CircleShape)
                .border(1.dp, AlertCardBorder, CircleShape)
        ) {
            Text(
                text       = (index + 1).toString(),
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color      = AlertRed
            )
        }

        // Alert info
        Column(
            modifier            = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text       = alert.watchName.ifBlank { "Huawei Watch" },
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = TextPrimary
                )
                Text(
                    text     = "offline",
                    fontSize = 11.sp,
                    color    = AlertRed,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(AlertCardBg, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Text(
                text     = formatDate(alert.timestamp),
                fontSize = 14.sp,
                color    = TextSecond
            )
        }

        // Time ago
        Text(
            text     = timeAgo(alert.timestamp),
            fontSize = 13.sp,
            color    = TextMuted
        )
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd MMM 'at' HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun timeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val mins = (diff / 60_000).toInt()
    return when {
        mins < 1    -> "Just now"
        mins < 60   -> "${mins}m ago"
        mins < 1440 -> "${mins / 60}h ago"
        else        -> "${mins / 1440}d ago"
    }
}
