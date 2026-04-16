package com.Anchor.watchguardian

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.Anchor.watchguardian.data.UserSession
import com.Anchor.watchguardian.services.WatchMonitorService
import com.Anchor.watchguardian.ui.screens.*
import com.Anchor.watchguardian.ui.screens.GeofenceScreen
import com.Anchor.watchguardian.ui.theme.AnchorTheme
import com.Anchor.watchguardian.viewmodel.ContactsViewModel
import com.Anchor.watchguardian.viewmodel.GeofenceViewModel
import com.Anchor.watchguardian.viewmodel.HomeViewModel
import com.huawei.hms.common.ApiException
import com.huawei.hms.push.HmsMessaging
import com.huawei.hms.support.hwid.HuaweiIdAuthManager
import com.huawei.hms.support.hwid.request.HuaweiIdAuthParams
import com.huawei.hms.support.hwid.request.HuaweiIdAuthParamsHelper

private const val TAG = "MainActivity"

// Navigation route names
private const val ROUTE_LOGIN    = "login"
private const val ROUTE_HOME     = "home"
private const val ROUTE_CONTACTS = "contacts"
private const val ROUTE_ALERTS   = "alert_history"
private const val ROUTE_GEOFENCE = "geofence"

/**
 * Single-activity host for the entire phone companion app.
 *
 * Responsibilities:
 *   1. Request runtime permissions (SMS, notifications, Bluetooth)
 *   2. Initialize WatchMonitorService (WearEngine)
 *   3. Register push token with HMS
 *   4. Handle HMS Account sign-in result (onActivityResult)
 *   5. Host the Compose NavHost with four screens:
 *        login → home → contacts / alert_history
 *
 * Mirrors the combined responsibilities of EntryAbility.ets (initialization, routing)
 * and the ArkTS router.pushUrl() calls scattered across pages.
 */
class MainActivity : ComponentActivity() {

    // ViewModels scoped to this Activity — survive configuration changes
    private val homeViewModel:     HomeViewModel     by viewModels()
    private val contactsViewModel: ContactsViewModel by viewModels()
    private val geofenceViewModel: GeofenceViewModel by viewModels()

    // Permissions to request on first launch
    private val permissionsToRequest = buildList {
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.BLUETOOTH_CONNECT) // Android 12+
        add(Manifest.permission.BLUETOOTH_SCAN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS) // Android 13+
        }
    }.toTypedArray()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            grants.forEach { (perm, granted) ->
                Log.i(TAG, "Permission $perm: ${if (granted) "granted" else "denied"}")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request runtime permissions
        requestMissingPermissions()

        // Initialize WearEngine so WatchMonitorService can obtain device + p2p clients
        WatchMonitorService.getInstance().init(this)

        // Register HMS push token — mirrors PushService.getPushToken() in EntryAbility.ets
        registerHmsPushToken()

        // Check if a push notification tap requested direct navigation to a specific screen
        val deepLinkRoute = intent.getStringExtra("navigate_to")

        setContent {
            AnchorTheme {
                val navController = rememberNavController()

                // Determine start destination: logged-in users go straight to home
                val startDestination = when {
                    deepLinkRoute != null            -> deepLinkRoute
                    UserSession.isLoggedIn(this)     -> ROUTE_HOME
                    else                             -> ROUTE_LOGIN
                }

                NavHost(navController = navController, startDestination = startDestination) {

                    composable(ROUTE_LOGIN) {
                        LoginScreen(
                            onLoginSuccess = {
                                navController.navigate(ROUTE_HOME) {
                                    popUpTo(ROUTE_LOGIN) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(ROUTE_HOME) {
                        HomeScreen(
                            viewModel           = homeViewModel,
                            onNavigateContacts  = { navController.navigate(ROUTE_CONTACTS) },
                            onNavigateAlerts    = { navController.navigate(ROUTE_ALERTS) },
                            onNavigateGeofence  = { navController.navigate(ROUTE_GEOFENCE) },
                            onSignOut           = {
                                WatchMonitorService.getInstance().stopMonitoring()
                                UserSession.logout(this@MainActivity)
                                navController.navigate(ROUTE_LOGIN) {
                                    popUpTo(ROUTE_HOME) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(ROUTE_CONTACTS) {
                        ContactsScreen(
                            viewModel = contactsViewModel,
                            onBack    = { navController.popBackStack() }
                        )
                    }

                    composable(ROUTE_ALERTS) {
                        AlertHistoryScreen(
                            viewModel = homeViewModel,
                            onBack    = { navController.popBackStack() }
                        )
                    }

                    composable(ROUTE_GEOFENCE) {
                        GeofenceScreen(
                            viewModel = geofenceViewModel,
                            onBack    = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    /**
     * Handle HMS Account sign-in result.
     * Called after LoginScreen launches activity.startActivityForResult(signInIntent, RC_SIGN_IN).
     * Mirrors: authController.executeRequest callback in LoginPage.ets.
     */
    @Deprecated("Deprecated in Java — required for HMS Account Kit sign-in flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val authParams = HuaweiIdAuthParamsHelper(HuaweiIdAuthParams.DEFAULT_AUTH_REQUEST_PARAM)
                .setProfile()
                .setIdToken()
                .createParams()
            val service = HuaweiIdAuthManager.getService(this, authParams)
            val task = service.parseAuthResultFromIntent(data)
            task.addOnSuccessListener { huaweiId ->
                val openID  = huaweiId.openId       ?: ""
                val unionID = huaweiId.unionId       ?: ""
                val idToken = huaweiId.idToken       ?: ""
                Log.i(TAG, "HMS sign-in success — openID: $openID")
                UserSession.login(this, openID, unionID, idToken)
                // Compose recomposition picks up the new UserSession state automatically via
                // the NavHost start-destination logic; we recreate to trigger it cleanly.
                recreate()
            }
            task.addOnFailureListener { e ->
                val code = (e as? ApiException)?.statusCode ?: -1
                // Status 1001500001 = user cancelled — not an error
                if (code != 1001500001) {
                    Log.e(TAG, "HMS sign-in failed: code=$code msg=${e.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        WatchMonitorService.getInstance().stopMonitoring()
    }

    private fun requestMissingPermissions() {
        val missing = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing)
        }
    }

    /** Obtain HMS push token and save it for upstream cloud sync. */
    private fun registerHmsPushToken() {
        HmsMessaging.getInstance(this)
            .token
            .addOnSuccessListener { token ->
                Log.i(TAG, "HMS push token obtained")
                getSharedPreferences("anchor_push", MODE_PRIVATE)
                    .edit().putString("pushToken", token).apply()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "HMS push token failed: ${e.message}")
            }
    }
}
