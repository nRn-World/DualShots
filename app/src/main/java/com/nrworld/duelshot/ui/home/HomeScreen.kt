package com.nrworld.duelshot.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import com.nrworld.duelshot.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nrworld.duelshot.ui.AppViewModel
import com.nrworld.duelshot.ui.components.DuelIcon
import com.nrworld.duelshot.ui.components.DuelIconKind
import com.nrworld.duelshot.ui.theme.DuelBg
import com.nrworld.duelshot.ui.theme.DuelGold
import com.nrworld.duelshot.ui.theme.DuelGradient
import com.nrworld.duelshot.ui.theme.DuelLine
import com.nrworld.duelshot.ui.theme.DuelOrange
import com.nrworld.duelshot.ui.theme.DuelSurface
import com.nrworld.duelshot.ui.theme.DuelSurface2
import com.nrworld.duelshot.ui.theme.DuelText
import com.nrworld.duelshot.ui.theme.DuelTextFaint
import com.nrworld.duelshot.ui.theme.DuelTextMuted

private val Ink = Color(0xFF04060D)

/** Startmenyn i studiodesignen: logotyp, titel och menyknappar. */
@Composable
fun HomeScreen(vm: AppViewModel, onStart: () -> Unit, onOpenPremium: () -> Unit, onOpenSettings: () -> Unit) {
    var showAbout by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0E1018), Color(0xFF121522), Color(0xFF0A0C13))))
    ) {
        // Orange glöd överst
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(DuelOrange.copy(alpha = .22f), Color.Transparent),
                    radius = 900f
                )
            )
        )

        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            LogoTile()
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dual", color = DuelText, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = (-0.8).sp)
                Text(
                    "Shots",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp, brush = DuelGradient
                    )
                )
            }
            Text(
                "RECORD BOTH SIDES. AT ONCE.",
                color = DuelTextMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(44.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryMenuButton(onClick = onStart)
                MenuButton(DuelIconKind.SETTINGS, "Settings", onClick = onOpenSettings)
                PremiumMenuButton(isPremium = vm.isPremium, onClick = onOpenPremium)
                MenuButton(DuelIconKind.INFO, "About", onClick = { showAbout = true })
            }

            Spacer(Modifier.height(32.dp))
            CreditFooter()
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            containerColor = DuelSurface2,
            title = { Text("DualShots", color = DuelText, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Version 1.4 · Dual lens recording engine",
                    color = DuelTextMuted, fontFamily = FontFamily.Monospace, fontSize = 12.sp
                )
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("OK", color = DuelOrange) } }
        )
    }
}

/** "Made by nRnWorld" med klickbar sajt och en liten kopiera-knapp för mejlen. */
@Composable
private fun CreditFooter() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val email = "bynrnworld@gmail.com"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Made by nRnWorld",
            color = DuelTextFaint, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.sp
        )
        Text(
            "nrnworld.one",
            color = com.nrworld.duelshot.ui.theme.DuelOrangeLight, fontSize = 11.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://nrnworld.one")
                            )
                        )
                    }
                }
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(email, color = DuelTextMuted, fontSize = 11.sp)
            Box(
                modifier = Modifier.size(24.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(com.nrworld.duelshot.ui.theme.DuelSurface2)
                    .border(1.dp, DuelLine, androidx.compose.foundation.shape.CircleShape)
                    .clickable {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as? android.content.ClipboardManager
                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("email", email))
                        android.widget.Toast
                            .makeText(context, "Email copied", android.widget.Toast.LENGTH_SHORT)
                            .show()
                    },
                contentAlignment = Alignment.Center
            ) {
                DuelIcon(DuelIconKind.COPY, DuelTextMuted, 12.dp)
            }
        }
    }
}

@Composable
private fun LogoTile() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
        Box(
            Modifier.size(120.dp).clip(RoundedCornerShape(60.dp))
                .background(Brush.radialGradient(listOf(DuelOrange.copy(alpha = .45f), Color.Transparent)))
        )
        Image(
            painter = painterResource(R.drawable.ic_launcher_art),
            contentDescription = "DualShots",
            modifier = Modifier.size(84.dp)
        )
    }
}

@Composable
private fun PrimaryMenuButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DuelGradient)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color.Black.copy(alpha = .2f)),
            contentAlignment = Alignment.Center
        ) { DuelIcon(DuelIconKind.VIDEO, Ink, 18.dp) }
        Spacer(Modifier.width(14.dp))
        Text(
            "START RECORDING",
            color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, letterSpacing = 1.sp,
            modifier = Modifier.weight(1f)
        )
        DuelIcon(DuelIconKind.CHEVRON, Ink, 18.dp)
    }
}

@Composable
private fun MenuButton(icon: DuelIconKind, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF161A29))
            .border(1.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DuelIcon(icon, DuelOrange, 18.dp)
        Spacer(Modifier.width(14.dp))
        Text(label, color = Color(0xFFE2E8F0), fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
        DuelIcon(DuelIconKind.CHEVRON, DuelTextFaint, 16.dp)
    }
}

@Composable
private fun PremiumMenuButton(isPremium: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(DuelOrange.copy(alpha = .15f), DuelGold.copy(alpha = .15f))))
            .border(1.dp, DuelOrange.copy(alpha = .4f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DuelIcon(DuelIconKind.CROWN, DuelGold, 18.dp)
        Spacer(Modifier.width(14.dp))
        Text(
            if (isPremium) "Premium active" else "Premium Features",
            color = DuelGold, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f)
        )
        DuelIcon(DuelIconKind.CHEVRON, DuelGold, 16.dp)
    }
}
