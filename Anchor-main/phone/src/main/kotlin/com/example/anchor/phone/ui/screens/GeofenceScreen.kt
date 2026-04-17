package com.Anchor.watchguardian.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
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
 *     • "Set here" always enabled; shows GPS status so the user knows if a fix is ready
 *   - Leaflet map showing anchor pin + 30m safe circle + 50m alert circle (when anchor is set)
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
    val anchorCoords by viewModel.anchorCoords.collectAsState()
    val gpsStatus    by viewModel.gpsStatus.collectAsState()

    // Snackbar host for "no GPS fix" feedback
    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Background
    ) { innerPadding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- Header ---
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .background(White)
                    .border(0.5.dp, Color(0xFFE5E7EB))
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
                        "Geofence Monitor",
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- State card ---
            val stateStyle = stateStyle(driftState)

            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(stateStyle.cardBg, RoundedCornerShape(20.dp))
                    .border(1.5.dp, stateStyle.cardBorder, RoundedCornerShape(20.dp))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text       = stateStyle.label,
                    fontSize   = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color      = stateStyle.stateColor,
                    letterSpacing = 1.sp
                )
                if (driftState != DriftState.NOT_SET && distanceM > 0f) {
                    Text(
                        text     = "${distanceM.toInt()} m from anchor",
                        fontSize = 17.sp,
                        color    = TextSecond
                    )
                }
                Text(
                    text     = stateDescription(driftState),
                    fontSize = 15.sp,
                    color    = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Anchor section (map embedded inside) ---
            val mapLat = anchorCoords?.first  ?: 1.3484    // Nanyang Business School, NTU
            val mapLng = anchorCoords?.second ?: 103.6820

            Column(
                modifier            = Modifier
                    .fillMaxWidth(0.9f)
                    .background(White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Title row
                Text(
                    text       = "Home Anchor",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = TextSecond
                )

                // Coords + GPS status + buttons
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column(
                        modifier            = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text       = if (hasAnchor) anchorText else "No anchor set",
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color      = if (hasAnchor) TextPrimary else TextMuted
                        )
                        Text(
                            text  = gpsStatus,
                            fontSize = 13.sp,
                            color = when {
                                gpsStatus.contains("ready", ignoreCase = true) -> SafeGreen
                                gpsStatus.contains("No GPS", ignoreCase = true) -> DriftAmber
                                else -> TextMuted
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                val loc = viewModel.getLastLocation()
                                if (loc != null) {
                                    viewModel.setAnchorPoint(loc.latitude, loc.longitude)
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message  = "No GPS fix yet — start monitoring or wait outdoors",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.height(48.dp),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = DeepBlue)
                        ) {
                            Text("Set here", fontSize = 14.sp)
                        }

                        if (hasAnchor) {
                            OutlinedButton(
                                onClick  = { viewModel.clearAnchor() },
                                modifier = Modifier.height(48.dp),
                                shape    = RoundedCornerShape(12.dp),
                                colors   = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed)
                            ) {
                                Text("Clear", fontSize = 14.sp, color = AlertRed)
                            }
                        }
                    }
                }

                // Map — always visible inside this card
                if (hasAnchor) {
                    Text(
                        text     = "🟢 30 m safe zone   🟠 50 m alert zone",
                        fontSize = 12.sp,
                        color    = TextMuted
                    )
                }

                AnchorMapView(
                    lat        = mapLat,
                    lng        = mapLng,
                    showAnchor = hasAnchor,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Monitoring toggle ---
            Column(
                modifier            = Modifier
                    .fillMaxWidth(0.9f)
                    .background(White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text       = "Monitoring",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = TextSecond
                )

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text       = if (isMonitoring) "Active" else "Inactive",
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = if (isMonitoring) SafeGreen else TextMuted
                        )
                        Text(
                            text     = if (isMonitoring) "GPS + motion detection running"
                                       else "Tap Start to begin monitoring",
                            fontSize = 14.sp,
                            color    = TextMuted
                        )
                    }

                    Button(
                        onClick  = {
                            if (isMonitoring) viewModel.stopMonitoring()
                            else viewModel.startMonitoring()
                        },
                        modifier = Modifier.height(52.dp),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = if (isMonitoring) AlertRed else SafeGreen
                        )
                    ) {
                        Text(
                            text     = if (isMonitoring) "Stop" else "Start",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Info banner (first-time hint) ---
            if (!hasAnchor) {
                Row(
                    modifier              = Modifier
                        .fillMaxWidth(0.9f)
                        .background(BlueLight, RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment     = Alignment.Top
                ) {
                    Text(
                        text       = "ℹ",
                        fontSize   = 16.sp,
                        color      = DeepBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text     = "Go to the home location, tap Start to get a GPS fix, " +
                                   "then tap 'Set here' to save the anchor.",
                        fontSize = 14.sp,
                        color    = Color(0xFF1565C0),
                        lineHeight = 21.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ------------------------------------------------------------------
// Map composable — uses OpenStreetMap's embed URL directly in a WebView.
// No CDN, no JS dependencies, no reload-on-recompose issues.
// ------------------------------------------------------------------

@Composable
private fun AnchorMapView(
    lat:        Double,
    lng:        Double,
    showAnchor: Boolean = true,
    modifier:   Modifier = Modifier
) {
    // Bounding box — ~300 m radius around the point
    val delta  = 0.003
    val bbox   = "${lng - delta},${lat - delta},${lng + delta},${lat + delta}"
    // OSM embed URL: shows the map with a marker pin when showAnchor is true
    val url    = if (showAnchor)
        "https://www.openstreetmap.org/export/embed.html?bbox=$bbox&layer=mapnik&marker=$lat,$lng"
    else
        "https://www.openstreetmap.org/export/embed.html?bbox=$bbox&layer=mapnik"

    // key = url ensures the WebView only reloads when coords actually change,
    // not on every recomposition
    key(url) {
        AndroidView(
            modifier = modifier,
            factory  = { context ->
                WebView(context).apply {
                    webViewClient               = WebViewClient()
                    settings.javaScriptEnabled  = true
                    settings.domStorageEnabled  = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort    = true
                    loadUrl(url)
                }
            }
        )
    }
}

// ------------------------------------------------------------------
// State styling helpers
// ------------------------------------------------------------------

private data class StateStyle(
    val cardBg:     Color,
    val cardBorder: Color,
    val stateColor: Color,
    val label:      String
)

private fun stateStyle(state: DriftState): StateStyle = when (state) {
    DriftState.SAFE     -> StateStyle(SafeCardBg,         SafeCardBorder,   SafeGreen,  "SAFE")
    DriftState.DRIFTING -> StateStyle(
        Color(0xFFFFF8E1), Color(0xFFFFD54F), DriftAmber, "DRIFTING"
    )
    DriftState.ALERT    -> StateStyle(AlertCardBg,        AlertCardBorder,  AlertRed,   "ALERT")
    DriftState.NOT_SET  -> StateStyle(
        Color(0xFFF5F5F5), Color(0xFFDDDDDD), TextMuted,  "NOT SET"
    )
}

private fun stateDescription(state: DriftState): String = when (state) {
    DriftState.NOT_SET  -> "Set an anchor point to start monitoring"
    DriftState.SAFE     -> "Within safe zone — no action needed"
    DriftState.DRIFTING -> "Moving away from home — monitoring closely"
    DriftState.ALERT    -> "Left the safe zone — caregivers notified"
}
