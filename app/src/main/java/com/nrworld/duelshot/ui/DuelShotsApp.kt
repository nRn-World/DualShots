package com.nrworld.duelshot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nrworld.duelshot.ui.components.PremiumDialog
import com.nrworld.duelshot.ui.gallery.GalleryScreen
import com.nrworld.duelshot.ui.home.HomeScreen
import com.nrworld.duelshot.ui.record.RecordScreen
import com.nrworld.duelshot.ui.settings.SettingsScreen
import com.nrworld.duelshot.ui.theme.DuelBg
import com.nrworld.duelshot.ui.theme.DuelCyan
import com.nrworld.duelshot.ui.theme.DuelLine
import com.nrworld.duelshot.ui.theme.DuelTextMuted

private enum class Tab { HOME, GALLERY, SETTINGS }

@Composable
fun DuelShotsApp(vm: AppViewModel) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var recording by remember { mutableStateOf(false) }
    var showPremium by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(DuelBg)) {
        Box(modifier = Modifier.weight(1f)) {
            if (recording) {
                RecordScreen(vm = vm, onExit = { recording = false })
            } else {
                when (tab) {
                    Tab.HOME -> HomeScreen(vm, onStart = { recording = true }, onOpenPremium = { showPremium = true })
                    Tab.GALLERY -> GalleryScreen()
                    Tab.SETTINGS -> SettingsScreen(vm)
                }
            }
        }

        if (!recording) {
            BottomBar(
                current = tab,
                onSelect = { tab = it }
            )
        }
    }

    if (showPremium) {
        PremiumDialog(
            feature = "DuelShots Premium",
            onDismiss = { showPremium = false },
            onUpgrade = { vm.grantPremium(); showPremium = false }
        )
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DuelBg.copy(alpha = 0.96f))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tab.values().forEach { t ->
            val active = t == current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(t) }
                    .padding(vertical = 6.dp)
            ) {
                Text(t.icon(), color = if (active) DuelCyan else DuelTextMuted, fontSize = 20.sp)
                Spacer(Modifier.size(2.dp))
                Text(
                    t.label(),
                    color = if (active) DuelCyan else DuelTextMuted,
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

private fun Tab.icon() = when (this) {
    Tab.HOME -> "⌂"
    Tab.GALLERY -> "▶"
    Tab.SETTINGS -> "⚙"
}

private fun Tab.label() = when (this) {
    Tab.HOME -> "Home"
    Tab.GALLERY -> "Gallery"
    Tab.SETTINGS -> "Settings"
}
