package com.nrworld.duelshot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nrworld.duelshot.ui.AppViewModel
import com.nrworld.duelshot.ui.DuelShotsApp
import com.nrworld.duelshot.ui.theme.DuelBg
import com.nrworld.duelshot.ui.theme.DuelShotsTheme
import com.nrworld.duelshot.ui.theme.DuelText
import com.nrworld.duelshot.ui.theme.DuelTextMuted

/**
 * DuelShots – inspelning med fram- och bakåtkamera samtidigt via CameraX Dual Concurrent Camera.
 * Roten innehåller designsystem, permission-hantering och navigation (Home/Record/Settings/Gallery).
 */
class MainActivity : ComponentActivity() {

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private val permissionGranted = androidx.compose.runtime.mutableStateOf(false)

    // Registreras som ett fält så att den är klar innan activityn startat (krav från Android).
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            permissionGranted.value = result.values.all { it }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionGranted.value = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!permissionGranted.value) {
            permissionLauncher.launch(requiredPermissions)
        }

        setContent {
            DuelShotsTheme {
                val granted = permissionGranted.value
                if (granted) {
                    val vm: AppViewModel = viewModel()
                    DuelShotsApp(vm)
                } else {
                    PermissionPlaceholder(
                        onRequest = { permissionLauncher.launch(requiredPermissions) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionPlaceholder(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DuelBg)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "DuelShots behöver tillstånd för kamera och mikrofon " +
                "för att spela in från båda kamerorna samtidigt.",
            color = DuelTextMuted,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        Button(onClick = onRequest) {
            Text("Be om tillstånd", color = DuelText)
        }
    }
}
