package com.Anchor.watchguardian.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Anchor.watchguardian.data.UserSession
import com.Anchor.watchguardian.ui.theme.*
import com.huawei.hms.support.hwid.HuaweiIdAuthManager
import com.huawei.hms.support.hwid.request.HuaweiIdAuthParams
import com.huawei.hms.support.hwid.request.HuaweiIdAuthParamsHelper

/**
 * HMS Account sign-in screen.
 *
 * Mirrors LoginPage.ets (HarmonyOS) which used:
 *   - LoginWithHuaweiIDButton (AccountKit) → replaced by standard HMS HuaweiIdAuthManager
 *   - AppStorage for auth state → replaced by UserSession (SharedPreferences)
 *   - router.pushUrl → replaced by NavController (via onLoginSuccess callback)
 *
 * HMS sign-in is an intent-based flow (startActivityForResult), so the sign-in
 * button calls activity.startActivityForResult(service.signInIntent, RC_SIGN_IN).
 * The result is caught in MainActivity.onActivityResult and calls onLoginSuccess.
 */

/** Matches the request code handled in MainActivity.onActivityResult. */
const val RC_SIGN_IN = 1001

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context  = LocalContext.current
    val activity = context as Activity

    var isLoading by remember { mutableStateOf(false) }
    var errorMsg  by remember { mutableStateOf("") }

    // Build HMS Account auth params: request openId, unionId and idToken
    fun launchHmsSignIn() {
        isLoading = true
        errorMsg  = ""
        val authParams = HuaweiIdAuthParamsHelper(HuaweiIdAuthParams.DEFAULT_AUTH_REQUEST_PARAM)
            .setProfile()
            .setIdToken()
            .createParams()
        val service = HuaweiIdAuthManager.getService(activity, authParams)
        // Result handled in MainActivity.onActivityResult → RC_SIGN_IN
        @Suppress("DEPRECATION")
        activity.startActivityForResult(service.signInIntent, RC_SIGN_IN)
    }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(100.dp))

        // App brand — mirrors the logo + title block in LoginPage.ets
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text       = "ANCHOR",
                fontSize   = 26.sp,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary
            )
            Text(
                text      = "Stay connected with those who matter",
                fontSize  = 14.sp,
                color     = TextSecond,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(72.dp))

        if (errorMsg.isNotEmpty()) {
            Text(
                text     = errorMsg,
                fontSize = 13.sp,
                color    = AlertRed,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(bottom = 20.dp)
            )
        }

        if (isLoading) {
            CircularProgressIndicator(
                color    = HmsRed,
                modifier = Modifier
                    .size(36.dp)
                    .padding(bottom = 20.dp)
            )
        }

        // HMS sign-in button — replaces <LoginWithHuaweiIDButton> from AccountKit
        Button(
            onClick  = { if (!isLoading) launchHmsSignIn() },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape    = RoundedCornerShape(24.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = HmsRed)
        ) {
            Text("Sign in with Huawei ID", color = Color.White, fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // DEBUG ONLY — mirrors "Skip login (emulator debug)" button in LoginPage.ets
        Button(
            onClick = {
                UserSession.login(
                    context = context,
                    openID  = "debug-openid-001",
                    unionID = "debug-unionid-001",
                    idToken = "debug-token-001"
                )
                onLoginSuccess()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape    = RoundedCornerShape(22.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF444444))
        ) {
            Text("Skip login (debug)", color = Color.White, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text      = "By signing in you agree to our Terms of Service",
            fontSize  = 11.sp,
            color     = TextMuted,
            textAlign = TextAlign.Center
        )
    }
}
