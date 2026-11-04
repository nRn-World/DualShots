package com.nrworld.duelshot.ui.settings

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nrworld.duelshot.model.AspectOption
import com.nrworld.duelshot.model.CodecOption
import com.nrworld.duelshot.model.QualityOption
import com.nrworld.duelshot.model.RecordingMode
import com.nrworld.duelshot.ui.AppViewModel
import com.nrworld.duelshot.ui.components.DuelIcon
import com.nrworld.duelshot.ui.components.DuelIconKind
import com.nrworld.duelshot.ui.components.GoldButton
import com.nrworld.duelshot.ui.components.PremiumDialog
import com.nrworld.duelshot.ui.components.RadioDot
import com.nrworld.duelshot.ui.components.SectionLabel
import com.nrworld.duelshot.ui.components.SegmentedControl
import com.nrworld.duelshot.ui.components.SurfaceCard
import com.nrworld.duelshot.ui.theme.DuelAccent
import com.nrworld.duelshot.ui.theme.DuelGold
import com.nrworld.duelshot.ui.theme.DuelLine
import com.nrworld.duelshot.ui.theme.DuelOrange
import com.nrworld.duelshot.ui.theme.DuelSurface2
import com.nrworld.duelshot.ui.theme.DuelText
import com.nrworld.duelshot.ui.theme.DuelTextMuted

@Composable
fun SettingsScreen(vm: AppViewModel) {
    var premiumFeature by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DuelIcon(DuelIconKind.SETTINGS, DuelOrange, 22.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                "SETTINGS STUDIO",
                color = DuelText, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                fontSize = 16.sp, letterSpacing = 1.sp
            )
        }
        Spacer(Modifier.height(22.dp))

        SectionLabel("Recording Mode")
        RecordingMode.values().forEach { m ->
            RadioRow(
                title = m.title, subtitle = m.subtitle,
                selected = vm.mode == m,
                onClick = { vm.mode = m }
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("Camera Formats")
        SurfaceCard {
            Column {
                Text("Front Camera", color = DuelText, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                AspectChips(selected = vm.frontAspect) { vm.frontAspect = it }
            }
        }
        Spacer(Modifier.height(10.dp))
        SurfaceCard {
            Column {
                Text("Back Camera", color = DuelText, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                AspectChips(selected = vm.backAspect) { vm.backAspect = it }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("Video Quality")
        SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Resolution", color = DuelText, modifier = Modifier.weight(1f))
                SegmentedControl(
                    options = listOf("720p", "1080p", "4K"),
                    selectedIndex = vm.quality.ordinal,
                    onSelect = { idx ->
                        val q = QualityOption.values()[idx]
                        if (q.premium && !vm.isPremium) premiumFeature = q.label
                        else vm.quality = q
                    }
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        SectionLabel("Container / Codec")
        SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Format", color = DuelText, modifier = Modifier.weight(1f))
                SegmentedControl(
                    options = listOf("MP4", "HEVC"),
                    selectedIndex = vm.codec.ordinal,
                    onSelect = { idx ->
                        val c = CodecOption.values()[idx]
                        if (c.premium && !vm.isPremium) premiumFeature = c.label
                        else vm.codec = c
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SurfaceCard(borderColor = DuelGold.copy(alpha = .45f)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DuelIcon(DuelIconKind.CROWN, DuelGold, 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "PREMIUM FEATURES",
                        color = DuelGold, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 1.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
                listOf(
                    "More format options (4K, HEVC)",
                    "60fps & custom bitrate",
                    "Per-camera aspect control",
                    "No watermark"
                ).forEach {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        DuelIcon(DuelIconKind.CHECK, DuelAccent, 14.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(it, color = DuelTextMuted, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (vm.isPremium) {
                    Text("Premium active", color = DuelGold, fontWeight = FontWeight.Bold)
                } else {
                    GoldButton(onClick = { vm.grantPremium() }, text = "Go Premium")
                }
            }
        }
    }

    premiumFeature?.let { feature ->
        PremiumDialog(
            feature = feature,
            onDismiss = { premiumFeature = null },
            onUpgrade = { vm.grantPremium(); premiumFeature = null }
        )
    }
}

@Composable
private fun RadioRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    SurfaceCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = DuelText, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = DuelTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            RadioDot(selected)
        }
    }
}

@Composable
private fun AspectChips(selected: AspectOption, onSelect: (AspectOption) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        AspectOption.values().forEach { opt ->
            val isSel = opt == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSel) DuelAccent.copy(alpha = .16f) else DuelSurface2)
                    .border(1.dp, if (isSel) DuelAccent.copy(alpha = .5f) else DuelLine, RoundedCornerShape(10.dp))
                    .clickable { onSelect(opt) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    opt.label,
                    color = if (isSel) DuelText else DuelTextMuted,
                    fontSize = 12.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
