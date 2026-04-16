package com.Anchor.watchguardian.ui.screens

import android.annotation.SuppressLint
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
                    .border(0.5.dp, Color(0xFFEEEEEE))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
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

            Spacer(modifier = Modifier.height(16.dp))

            // --- State card ---
            val stateStyle = stateStyle(driftState)

            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(stateStyle.cardBg, RoundedCornerShape(16.dp))
                    .border(1.dp, stateStyle.cardBorder, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text       = stateStyle.label,
                    fontSize   = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color      = stateStyle.stateColor
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

            Spacer(modifier = Modifier.height(16.dp))

            // --- Anchor section ---
            Column(
                modifier            = Modifier
                    .fillMaxWidth(0.9f)
                    .background(White, RoundedCornerShape(12.dp))
                    .border(0.5.dp, Color(0xFFEEEEEE), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text       = "Home anchor",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color      = TextSecond
                )

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column(
                        modifier            = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text     = if (hasAnchor) anchorText else "No anchor set",
                            fontSize = 14.sp,
                            color    = if (hasAnchor) TextPrimary else TextMuted
                        )
                        // GPS fix status — helps the user know whether "Set here" will work
                        Text(
                            text     = gpsStatus,
                            fontSize = 11.sp,
                            color    = when {
                                gpsStatus.contains("ready", ignoreCase = true) -> SafeGreen
                                gpsStatus.contains("No GPS", ignoreCase = true) -> DriftAmber
                                else -> TextMuted
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Set anchor — always enabled; uses best available location
                        Button(
                            onClick = {
                                val loc = viewModel.getLastLocation()
                                if (loc != null) {
                                    viewModel.setAnchorPoint(loc.latitude, loc.longitude)
                                } else {
                                    // Show feedback instead of silently failing
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message  = "No GPS fix yet — start monitoring or wait outdoors",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            },
                            shape  = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TextPrimary)
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

            // --- Anchor map — only shown once an anchor is saved ---
            if (hasAnchor && anchorCoords != null) {
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier            = Modifier
                        .fillMaxWidth(0.9f)
                        .background(White, RoundedCornerShape(12.dp))
                        .border(0.5.dp, Color(0xFFEEEEEE), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text       = "Anchor location",
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color      = TextSecond
                    )
                    Text(
                        text     = "Green circle = 30 m safe zone · Amber = 50 m alert zone",
                        fontSize = 11.sp,
                        color    = TextMuted
                    )

                    val (lat, lng) = anchorCoords!!
                    AnchorMapView(
                        lat      = lat,
                        lng      = lng,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    )
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
                Text(
                    text       = "Monitoring",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color      = TextSecond
                )

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text       = if (isMonitoring) "Active" else "Inactive",
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color      = if (isMonitoring) SafeGreen else TextMuted
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

            // --- Info banner (first-time hint) ---
            if (!hasAnchor) {
                Row(
                    modifier              = Modifier
                        .fillMaxWidth(0.9f)
                        .background(BlueLight, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.Top
                ) {
                    Text(
                        text       = "i",
                        fontSize   = 13.sp,
                        color      = Color(0xFF0C447C),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text     = "Go to the home location, tap Start to get a GPS fix, " +
                                   "then tap 'Set here' to save the anchor. " +
                                   "You can also 'Set here' without starting monitoring if the GPS status shows 'GPS fix ready'.",
                        fontSize = 12.sp,
                        color    = Color(0xFF185FA5)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ------------------------------------------------------------------
// Leaflet map composable — embeds an OpenStreetMap tile layer in a WebView
// with an anchor marker, 30 m safe circle (green), and 50 m alert circle (amber).
// ------------------------------------------------------------------

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AnchorMapView(
    lat:      Double,
    lng:      Double,
    modifier: Modifier = Modifier
) {
    val html = buildLeafletHtml(lat, lng)

    AndroidView(
        modifier = modifier,
        factory  = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                loadDataWithBaseURL(
                    "https://unpkg.com",   // allows Leaflet CDN assets to load
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        update = { webView ->
            // Re-load when coordinates change (e.g. anchor updated)
            webView.loadDataWithBaseURL(
                "https://unpkg.com",
                buildLeafletHtml(lat, lng),
                "text/html",
                "UTF-8",
                null
            )
        }
    )
}

/** Builds the self-contained Leaflet HTML shown in the WebView. */
private fun buildLeafletHtml(lat: Double, lng: Double): String = """
<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
  <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html, body, #map { width: 100%; height: 100%; }
  </style>
</head>
<body>
  <div id="map"></div>
  <script>
    var map = L.map('map', { zoomControl: true, attributionControl: false })
               .setView([$lat, $lng], 17);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19
    }).addTo(map);

    // Safe zone — 30 m green circle
    L.circle([$lat, $lng], {
      radius: 30, color: '#1DAB5F', fillColor: '#1DAB5F', fillOpacity: 0.12, weight: 2
    }).addTo(map);

    // Alert zone — 50 m amber circle
    L.circle([$lat, $lng], {
      radius: 50, color: '#FBBF24', fillColor: '#FBBF24', fillOpacity: 0.08, weight: 2,
      dashArray: '6 4'
    }).addTo(map);

    // Anchor pin
    var icon = L.divIcon({
      html: '<div style="font-size:24px;line-height:1;">📍</div>',
      iconSize: [28, 28], iconAnchor: [14, 28], className: ''
    });
    L.marker([$lat, $lng], { icon: icon })
     .addTo(map)
     .bindPopup('Home anchor<br>${"%.5f".format(lat)}°, ${"%.5f".format(lng)}°')
     .openPopup();
  </script>
</body>
</html>
""".trimIndent()

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
