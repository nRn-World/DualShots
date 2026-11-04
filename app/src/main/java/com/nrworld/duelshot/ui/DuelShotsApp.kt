package com.nrworld.duelshot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.nrworld.duelshot.ui.components.DuelIcon
import com.nrworld.duelshot.ui.components.DuelIconKind
import com.nrworld.duelshot.ui.components.PremiumDialog
import com.nrworld.duelshot.ui.gallery.GalleryScreen
import com.nrworld.duelshot.ui.home.HomeScreen
import com.nrworld.duelshot.ui.record.RecordScreen
import com.nrworld.duelshot.ui.settings.SettingsScreen
import com.nrworld.duelshot.ui.theme.DuelBg
import com.nrworld.duelshot.ui.theme.DuelCyan
import com.nrworld.duelshot.ui.theme.DuelLine
import com.nrworld.duelshot.ui.theme.DuelSurface2
import com.nrworld.duelshot.ui.theme.DuelTextFaint

private enum class Tab(val icon: DuelIconKind, val label: String) {
    HOME(DuelIconKind.HOME, "Home"),
    GALLERY(DuelIconKind.GALLERY, "Gallery"),
    SETTINGS(DuelIconKind.SETTINGS, "Settings")
}

@Composable
fun DuelShotsApp(vm: AppViewModel) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var recording by remember { mutableStateOf(false) }
    var showPremium by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(DuelBg).statusBarsPadding()) {
        Box(modifier = Modifier.weight(1f)) {
            if (recording) {
                RecordScreen(vm = vm, onExit = { recording = false })
            } else {
                when (tab) {
                    Tab.HOME -> HomeScreen(
                        vm,
                        onStart = { recording = true },
                        onOpenPremium = { showPremium = true },
                        onOpenSettings = { tab = Tab.SETTINGS }
                    )
                    Tab.GALLERY -> GalleryScreen()
                    Tab.SETTINGS -> SettingsScreen(vm)
                }
            }
        }

        if (!recording) {
            BottomBar(current = tab, onSelect = { tab = it })
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
    Column(Modifier.fillMaxWidth().background(DuelBg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(DuelLine))
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Tab.values().forEach { t ->
                val active = t == current
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelect(t) }
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (active) DuelSurface2 else Color.Transparent)
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                    ) {
                        DuelIcon(t.icon, if (active) Color.White else DuelTextFaint, 22.dp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        t.label,
                        color = if (active) DuelCyan else DuelTextFaint,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
