package com.Anchor.watchguardian.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
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

const val RC_SIGN_IN = 1001

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context  = LocalContext.current
    val activity = context as Activity

    var isLoading by remember { mutableStateOf(false) }
    var errorMsg  by remember { mutableStateOf("") }

    fun launchHmsSignIn() {
        isLoading = true
        errorMsg  = ""
        val authParams = HuaweiIdAuthParamsHelper(HuaweiIdAuthParams.DEFAULT_AUTH_REQUEST_PARAM)
            .setProfile()
            .setIdToken()
            .createParams()
        val service = HuaweiIdAuthManager.getService(activity, authParams)
        @Suppress("DEPRECATION")
        activity.startActivityForResult(service.signInIntent, RC_SIGN_IN)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // --- Brand ---
            Text(
                text       = "⚓",
                fontSize   = 64.sp,
                textAlign  = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text       = "ANCHOR",
                fontSize   = 36.sp,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text      = "Keep your loved ones safe.\nGet alerted the moment they need you.",
                fontSize  = 16.sp,
                color     = TextSecond,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(56.dp))

            // --- Error ---
            if (errorMsg.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AlertCardBg, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text      = errorMsg,
                        fontSize  = 15.sp,
                        color     = AlertRed,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // --- Loading ---
            if (isLoading) {
                CircularProgressIndicator(
                    color    = DeepBlue,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // --- Huawei sign-in ---
            Button(
                onClick  = { if (!isLoading) launchHmsSignIn() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = HmsRed)
            ) {
                Text(
                    text     = "Sign in with Huawei ID",
                    color    = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // DEBUG — emulator skip
            OutlinedButton(
                onClick  = {
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
                    .height(52.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = OutlinedButtonDefaults.outlinedButtonColors(contentColor = TextSecond)
            ) {
                Text("Skip login (debug)", fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text      = "By signing in you agree to our Terms of Service",
                fontSize  = 13.sp,
                color     = TextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}
