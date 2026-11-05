package com.nrworld.duelshot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nrworld.duelshot.data.AppPrefs
import com.nrworld.duelshot.ui.AppViewModel
import com.nrworld.duelshot.ui.DuelShotsApp
import com.nrworld.duelshot.ui.components.DuelIcon
import com.nrworld.duelshot.ui.components.DuelIconKind
import com.nrworld.duelshot.ui.onboarding.OnboardingScreen
import com.nrworld.duelshot.ui.theme.DuelBg
import com.nrworld.duelshot.ui.theme.DuelGradient
import com.nrworld.duelshot.ui.theme.DuelLine
import com.nrworld.duelshot.ui.theme.DuelOrange
import com.nrworld.duelshot.ui.theme.DuelShotsTheme
import com.nrworld.duelshot.ui.theme.DuelSurface2
import com.nrworld.duelshot.ui.theme.DuelText
import com.nrworld.duelshot.ui.theme.DuelTextMuted

/**
 * DualShots – inspelning med fram- och bakkamera samtidigt via CameraX Dual Concurrent Camera.
 * Roten hanterar behörigheter, första-start-onboarding och navigation.
 */
class MainActivity : ComponentActivity() {

    private val requiredPermissions: Array<String> = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        // Läsa tillbaka inspelade klipp i Gallery. Android 13+ använder READ_MEDIA_VIDEO,
        // äldre versioner READ_EXTERNAL_STORAGE.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_VIDEO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
    )

    private val permissionGranted = mutableStateOf(false)
    private val askedOnce = mutableStateOf(false)

    // Registreras som ett fält så att den är klar innan activityn startat (krav från Android).
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            askedOnce.value = true
            permissionGranted.value = requiredPermissions.all { permission ->
                result[permission] == true ||
                    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            }
        }

    private fun allGranted(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionGranted.value = allGranted()
        if (!permissionGranted.value) permissionLauncher.launch(requiredPermissions)

        setContent {
            DuelShotsTheme {
                val prefs = remember { AppPrefs(applicationContext) }
                var onboarded by remember { mutableStateOf(prefs.onboarded) }

                when {
                    !permissionGranted.value -> PermissionsScreen(
                        denied = askedOnce.value,
                        onRequest = { permissionLauncher.launch(requiredPermissions) },
                        onOpenSettings = {
                            runCatching {
                                startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", packageName, null)
                                    )
                                )
                            }
                        }
                    )

                    !onboarded -> OnboardingScreen(onDone = {
                        prefs.onboarded = true
                        onboarded = true
                    })

                    else -> {
                        val vm: AppViewModel = viewModel()
                        DuelShotsApp(vm)
                    }
                }
            }
        }
    }

    // Tillbaka från systeminställningarna: plocka upp ett nyligen beviljat tillstånd.
    override fun onResume() {
        super.onResume()
        permissionGranted.value = allGranted()
    }
}

@Composable
private fun PermissionsScreen(denied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(DuelBg).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.25f))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(136.dp)) {
            Box(
                Modifier
                    .size(136.dp)
                    .clip(RoundedCornerShape(68.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(DuelOrange.copy(alpha = .40f), Color.Transparent)
                        )
                    )
            )
            Image(
                painter = painterResource(R.drawable.ic_launcher_art),
                contentDescription = "DualShots",
                modifier = Modifier.size(96.dp)
            )
        }

        Spacer(Modifier.height(26.dp))
        Text(
            "Camera, microphone & photos", color = DuelText, fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (denied) {
                "DualShots needs these to record two cameras at once and read your recordings back in Gallery. Grant them in system settings and come back."
            } else {
                "DualShots records the front and back camera at the same time and reads recordings back in Gallery. All permissions stay on your device."
            },
            color = DuelTextMuted, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(0.3f))

        Box(
            modifier = Modifier.fillMaxWidth().height(54.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(DuelGradient)
                .clickable(onClick = onRequest),
            contentAlignment = Alignment.Center
        ) {
            Text("Allow access", color = Color(0xFF04060D), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        if (denied) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(50.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(DuelSurface2)
                    .border(1.dp, DuelLine, RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center
            ) {
                Text("Open settings", color = DuelText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            TextButton(onClick = onOpenSettings) {
                Text("Already allowed? Come back here", color = DuelTextMuted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}
