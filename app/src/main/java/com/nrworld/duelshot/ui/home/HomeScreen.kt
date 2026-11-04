package com.nrworld.duelshot.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nrworld.duelshot.model.RecordingMode
import com.nrworld.duelshot.ui.AppViewModel
import com.nrworld.duelshot.ui.components.DuelIcon
import com.nrworld.duelshot.ui.components.DuelIconKind
import com.nrworld.duelshot.ui.components.DuelLogoMark
import com.nrworld.duelshot.ui.components.GradientButton
import com.nrworld.duelshot.ui.components.ModeDiagram
import com.nrworld.duelshot.ui.components.RadioDot
import com.nrworld.duelshot.ui.components.SectionLabel
import com.nrworld.duelshot.ui.components.SurfaceCard
import com.nrworld.duelshot.ui.components.WordmarkText
import com.nrworld.duelshot.ui.theme.DuelCyan
import com.nrworld.duelshot.ui.theme.DuelGold
import com.nrworld.duelshot.ui.theme.DuelGradient
import com.nrworld.duelshot.ui.theme.DuelLine
import com.nrworld.duelshot.ui.theme.DuelLineStrong
import com.nrworld.duelshot.ui.theme.DuelSurface
import com.nrworld.duelshot.ui.theme.DuelSurface2
import com.nrworld.duelshot.ui.theme.DuelText
import com.nrworld.duelshot.ui.theme.DuelTextFaint
import com.nrworld.duelshot.ui.theme.DuelTextMuted

private val Ink = Color(0xFF04060D)

@Composable
fun HomeScreen(vm: AppViewModel, onStart: () -> Unit, onOpenPremium: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DuelLogoMark(size = 40.dp)
            Spacer(Modifier.width(10.dp))
            WordmarkText(fontSize = 24.sp)
            Spacer(Modifier.weight(1f))
            PlanBadge(vm.isPremium)
        }

        Spacer(Modifier.height(24.dp))
        HeroCard(vm, onStart)

        Spacer(Modifier.height(30.dp))
        SectionLabel("Recording mode")
        RecordingMode.values().forEach { m ->
            ModeCard(mode = m, selected = vm.mode == m, onClick = { vm.mode = m })
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(14.dp))
        SectionLabel("Current setup")
        SetupCard(vm, onClick = onOpenSettings)

        if (!vm.isPremium) {
            Spacer(Modifier.height(14.dp))
            PremiumTeaser(onOpenPremium)
        }
    }
}

@Composable
private fun HeroCard(vm: AppViewModel, onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF151C38), DuelSurface)))
            .border(1.dp, DuelLineStrong, RoundedCornerShape(28.dp))
            .padding(22.dp)
    ) {
        DuelLogoMark(size = 56.dp)
        Spacer(Modifier.height(18.dp))
        Text(
            "Capture more.",
            color = DuelText, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, letterSpacing = (-1).sp
        )
        Text(
            "From every angle.",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, brush = DuelGradient
            )
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Front and back camera, recorded at the same time.",
            color = DuelTextMuted, fontSize = 14.sp
        )
        Spacer(Modifier.height(22.dp))
        GradientButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(Ink))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Start recording", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    "${vm.mode.title} · ${vm.quality.label}",
                    color = Ink.copy(alpha = .7f), fontSize = 12.sp
                )
            }
            DuelIcon(DuelIconKind.CHEVRON, Ink, 18.dp)
        }
    }
}

@Composable
private fun ModeCard(mode: RecordingMode, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) DuelSurface2 else DuelSurface)
            .border(1.dp, if (selected) DuelCyan.copy(alpha = .5f) else DuelLine, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModeDiagram(
            mode = mode,
            color = if (selected) DuelCyan else DuelTextFaint,
            width = 56.dp, height = 40.dp
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(mode.title, color = DuelText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(mode.subtitle, color = DuelTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        RadioDot(selected)
    }
}

@Composable
private fun SetupCard(vm: AppViewModel, onClick: () -> Unit) {
    SurfaceCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth()) {
                    KeyValue("Back", vm.backAspect.label, Modifier.weight(1f))
                    KeyValue("Front", vm.frontAspect.label, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    KeyValue("Quality", vm.quality.label, Modifier.weight(1f))
                    KeyValue("Format", vm.codec.label, Modifier.weight(1f))
                }
            }
            DuelIcon(DuelIconKind.CHEVRON, DuelTextFaint, 18.dp)
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(key.uppercase(), color = DuelTextFaint, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Text(value, color = DuelText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun PremiumTeaser(onOpenPremium: () -> Unit) {
    SurfaceCard(onClick = onOpenPremium, borderColor = DuelGold.copy(alpha = .4f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DuelIcon(DuelIconKind.GEM, DuelGold, 24.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Go Premium", color = DuelText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "4K · 60fps · HEVC · watermark-free",
                    color = DuelTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)
                )
            }
            DuelIcon(DuelIconKind.CHEVRON, DuelGold, 18.dp)
        }
    }
}

@Composable
private fun PlanBadge(premium: Boolean) {
    Row(
        modifier = Modifier.clip(CircleShape)
            .background(if (premium) DuelGold else DuelSurface2)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DuelIcon(DuelIconKind.GEM, if (premium) Ink else DuelTextMuted, 12.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            if (premium) "Premium" else "Free",
            color = if (premium) Ink else DuelTextMuted,
            fontSize = 12.sp, fontWeight = FontWeight.Bold
        )
    }
}
