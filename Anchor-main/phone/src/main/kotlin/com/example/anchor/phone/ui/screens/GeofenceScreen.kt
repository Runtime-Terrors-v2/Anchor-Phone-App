package com.Anchor.watchguardian.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Anchor.watchguardian.data.model.DriftState
import com.Anchor.watchguardian.ui.theme.*
import com.Anchor.watchguardian.viewmodel.GeofenceViewModel

/**
 * Geofence monitor screen — phone-side mirror of the watch's main status view.
 *
 * Shows:
 *   - Large state card (SAFE / DRIFTING / ALERT / NOT_SET) with colour coding
 *   - Distance from anchor in metres
 *   - Anchor management (set from current GPS fix / clear)
 *   - Monitoring toggle (start / stop GPS + motion detection)
 */
@Composable
fun GeofenceScreen(
    viewModel: GeofenceViewModel,
    onBack:    () -> Unit
) {
    val driftState   by viewModel.driftState.collectAsState()
    val distanceM    by viewModel.distanceM.collectAsState()
    val isMonitoring by viewModel.isMonitoring.collectAsState()
    val hasAnchor    by viewModel.hasAnchor.collectAsState()
    val anchorText   by viewModel.anchorText.collectAsState()

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
                .border(0.5.dp, Color(0xFFEEEEEE))
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onBack) {
                    Text("\u2190", fontSize = 20.sp, color = TextPrimary)
                }
                Text(
                    "Geofence monitor",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color      = TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- State card ---
        val (cardBg, cardBorder, stateColor, stateLabel) = stateStyle(driftState)

        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(cardBg, RoundedCornerShape(16.dp))
                .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text       = stateLabel,
                fontSize   = 28.sp,
                fontWeight = FontWeight.Bold,
                color      = stateColor
            )
            if (driftState != DriftState.NOT_SET && distanceM > 0f) {
                Text(
                    text     = "${distanceM.toInt()} m from anchor",
                    fontSize = 15.sp,
                    color    = TextSecond
                )
            }
            Text(
                text     = stateDescription(driftState),
                fontSize = 13.sp,
                color    = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- Anchor section ---
        Column(
            modifier            = Modifier
                .fillMaxWidth(0.9f)
                .background(White, RoundedCornerShape(12.dp))
                .border(0.5.dp, Color(0xFFEEEEEE), RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Home anchor", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecond)

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text     = if (hasAnchor) anchorText else "No anchor set",
                        fontSize = 14.sp,
                        color    = if (hasAnchor) TextPrimary else TextMuted
                    )
                    if (!hasAnchor) {
                        Text(
                            text     = "Tap 'Set here' while on-location",
                            fontSize = 12.sp,
                            color    = TextMuted
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Set anchor from current GPS fix
                    Button(
                        onClick = {
                            val loc = viewModel.getLastLocation()
                            if (loc != null) {
                                viewModel.setAnchorPoint(loc.latitude, loc.longitude)
                            }
                        },
                        shape  = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TextPrimary),
                        enabled = isMonitoring // GPS must be running to have a fresh fix
                    ) {
                        Text("Set here", fontSize = 12.sp)
                    }

                    if (hasAnchor) {
                        OutlinedButton(
                            onClick = { viewModel.clearAnchor() },
                            shape   = RoundedCornerShape(8.dp)
                        ) {
                            Text("Clear", fontSize = 12.sp, color = AlertRed)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Monitoring toggle ---
        Column(
            modifier            = Modifier
                .fillMaxWidth(0.9f)
                .background(White, RoundedCornerShape(12.dp))
                .border(0.5.dp, Color(0xFFEEEEEE), RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Monitoring", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecond)

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text     = if (isMonitoring) "Active" else "Inactive",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color    = if (isMonitoring) SafeGreen else TextMuted
                    )
                    Text(
                        text     = if (isMonitoring) "GPS + motion detection running"
                                   else "Tap Start to begin monitoring",
                        fontSize = 12.sp,
                        color    = TextMuted
                    )
                }

                Button(
                    onClick = {
                        if (isMonitoring) viewModel.stopMonitoring()
                        else viewModel.startMonitoring()
                    },
                    shape  = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMonitoring) AlertRed else SafeGreen
                    )
                ) {
                    Text(
                        text     = if (isMonitoring) "Stop" else "Start",
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Info banner ---
        if (!isMonitoring && !hasAnchor) {
            Row(
                modifier              = Modifier
                    .fillMaxWidth(0.9f)
                    .background(BlueLight, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.Top
            ) {
                Text("i", fontSize = 13.sp, color = Color(0xFF0C447C), fontWeight = FontWeight.Bold)
                Text(
                    text     = "Start monitoring first to get a GPS fix, then tap 'Set here' " +
                               "while standing at the home location to save the anchor.",
                    fontSize = 12.sp,
                    color    = Color(0xFF185FA5)
                )
            }
        }
    }
}

// --- Helpers ---

private data class StateStyle(
    val cardBg:     Color,
    val cardBorder: Color,
    val stateColor: Color,
    val label:      String
)

private fun stateStyle(state: DriftState): StateStyle = when (state) {
    DriftState.SAFE     -> StateStyle(SafeCardBg,   SafeCardBorder,   SafeGreen,   "SAFE")
    DriftState.DRIFTING -> StateStyle(
        Color(0xFFFFF8E1), Color(0xFFFFD54F), DriftAmber, "DRIFTING"
    )
    DriftState.ALERT    -> StateStyle(AlertCardBg,  AlertCardBorder,  AlertRed,    "ALERT")
    DriftState.NOT_SET  -> StateStyle(
        Color(0xFFF5F5F5), Color(0xFFDDDDDD), TextMuted, "NOT SET"
    )
}

private fun stateDescription(state: DriftState): String = when (state) {
    DriftState.NOT_SET  -> "Set an anchor point to start monitoring"
    DriftState.SAFE     -> "Within safe zone — no action needed"
    DriftState.DRIFTING -> "Moving away from home — monitoring closely"
    DriftState.ALERT    -> "Left the safe zone — caregivers notified"
}
