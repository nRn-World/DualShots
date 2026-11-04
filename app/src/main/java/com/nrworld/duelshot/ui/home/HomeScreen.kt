package com.nrworld.duelshot.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nrworld.duelshot.model.RecordingMode
import com.nrworld.duelshot.ui.AppViewModel
import com.nrworld.duelshot.ui.components.DuelLogoMark
import com.nrworld.duelshot.ui.components.GradientButton
import com.nrworld.duelshot.ui.components.SectionLabel
import com.nrworld.duelshot.ui.components.SurfaceCard
import com.nrworld.duelshot.ui.components.WordmarkText
import com.nrworld.duelshot.ui.theme.DuelGold
import com.nrworld.duelshot.ui.theme.DuelMagenta
import com.nrworld.duelshot.ui.theme.DuelSurface2
import com.nrworld.duelshot.ui.theme.DuelText
import com.nrworld.duelshot.ui.theme.DuelTextMuted

@Composable
fun HomeScreen(vm: AppViewModel, onStart: () -> Unit, onOpenPremium: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            DuelLogoMark(size = 44.dp)
            Spacer(Modifier.size(12.dp))
            WordmarkText(fontSize = 26.sp)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier.clip(CircleShape)
                    .background(if (vm.isPremium) DuelGold else DuelSurface2)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    if (vm.isPremium) "♛ Premium" else "Free",
                    color = if (vm.isPremium) Color(0xFF241703) else DuelTextMuted,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Text("Capture More. From Every Angle.", color = DuelTextMuted, fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))

        GradientButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text("🎥", fontSize = 20.sp)
            Spacer(Modifier.size(12.dp))
            Column {
                Text("Start Recording", color = Color(0xFF04060D), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Choose your mode", color = Color(0xFF04060D).copy(alpha = .7f), fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel("Recording Mode")
        RecordingMode.values().forEach { m ->
            ModeRow(
                mode = m,
                selected = vm.mode == m,
                onClick = { vm.mode = m }
            )
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(8.dp))
        SectionLabel("Quick Settings")
        SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("⚡  ${vm.frontAspect.label} · ${vm.backAspect.label}", color = DuelText, fontWeight = FontWeight.SemiBold)
                    Text("${vm.quality.label} · ${vm.codec.label}", color = DuelTextMuted, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp))
                }
                Text("›", color = DuelTextMuted, fontSize = 22.sp)
            }
        }

        if (!vm.isPremium) {
            Spacer(Modifier.height(16.dp))
            SurfaceCard(onClick = onOpenPremium) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("♛", color = DuelGold, fontSize = 20.sp)
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Go Premium", color = DuelText, fontWeight = FontWeight.Bold)
                        Text("4K · 60fps · HEVC · fri format · utan watermark", color = DuelTextMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeRow(mode: RecordingMode, selected: Boolean, onClick: () -> Unit) {
    SurfaceCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape)
                    .background(if (selected) DuelMagenta.copy(alpha = .22f) else DuelSurface2),
                contentAlignment = Alignment.Center
            ) {
                Text(modeIcon(mode), color = if (selected) DuelMagenta else DuelTextMuted, fontSize = 18.sp)
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(mode.title, color = DuelText, fontWeight = FontWeight.SemiBold)
                Text(mode.subtitle, color = DuelTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Box(
                modifier = Modifier.size(20.dp).clip(CircleShape)
                    .background(if (selected) DuelMagenta else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                if (selected) Text("✓", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

private fun modeIcon(m: RecordingMode) = when (m) {
    RecordingMode.DUAL_VIEW -> "▢"
    RecordingMode.SEPARATE -> "▯"
    RecordingMode.BOTH -> "⇄"
}
