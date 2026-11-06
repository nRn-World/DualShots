package com.nrworld.duelshot.ui

import android.widget.Toast
import com.nrworld.duelshot.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.nrworld.duelshot.ui.components.LocalRefFont
import com.nrworld.duelshot.ui.components.LocalRefScale
import com.nrworld.duelshot.ui.components.LocalRefStretch
import com.nrworld.duelshot.ui.components.PremiumDialog
import com.nrworld.duelshot.ui.components.RefBg
import com.nrworld.duelshot.ui.components.RefIcon
import com.nrworld.duelshot.ui.components.RefIconKind
import com.nrworld.duelshot.ui.components.RefOrange
import com.nrworld.duelshot.ui.components.rp
import com.nrworld.duelshot.ui.components.rs
import com.nrworld.duelshot.ui.gallery.ConfirmDelete
import com.nrworld.duelshot.ui.gallery.DemoEntries
import com.nrworld.duelshot.ui.gallery.GalleryScreen
import com.nrworld.duelshot.ui.gallery.LibraryEntry
import com.nrworld.duelshot.ui.gallery.MediaDialog
import com.nrworld.duelshot.ui.gallery.MediaItem
import com.nrworld.duelshot.ui.gallery.deleteItem
import com.nrworld.duelshot.ui.gallery.queryLibrary
import com.nrworld.duelshot.ui.gallery.shareItem
import com.nrworld.duelshot.ui.gallery.toEntry
import com.nrworld.duelshot.ui.home.HomeScreen
import com.nrworld.duelshot.ui.record.RecordScreen
import com.nrworld.duelshot.ui.settings.SettingsScreen
import com.nrworld.duelshot.ui.stats.StatsScreen
import com.nrworld.duelshot.ui.theme.DuelOrange
import com.nrworld.duelshot.ui.theme.DuelSurface2
import com.nrworld.duelshot.ui.theme.DuelText
import com.nrworld.duelshot.ui.theme.DuelTextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Tab(val icon: RefIconKind, val label: String) {
    HOME(RefIconKind.HOME, "Home"),
    GALLERY(RefIconKind.GALLERY, "Gallery"),
    STATS(RefIconKind.STATS, "Stats"),
    SETTINGS(RefIconKind.SLIDERS, "Settings")
}

@Composable
fun DuelShotsApp(vm: AppViewModel) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.HOME) }
    var recording by remember { mutableStateOf(false) }
    var showPremium by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showTips by remember { mutableStateOf(false) }
    var openItem by remember { mutableStateOf<MediaItem?>(null) }
    var pendingDelete by remember { mutableStateOf<MediaItem?>(null) }
    var library by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }

    // Biblioteket läses om när man lämnar inspelningen eller raderar något.
    LaunchedEffect(reload, recording) {
        if (!recording) library = withContext(Dispatchers.IO) { queryLibrary(context) }
    }

    val soon: (String) -> Unit = { what -> Toast.makeText(context, "$what is coming soon", Toast.LENGTH_SHORT).show() }
    val openEntry: (LibraryEntry) -> Unit = { e ->
        if (e.item != null) openItem = e.item
        else Toast.makeText(context, "Sample clip – record a take to see your own here", Toast.LENGTH_SHORT).show()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(RefBg)) {
        // Skärmarna är ritade i referensbildernas pixlar (1024 breda, 2:3). Telefoner är
        // högre: höjder/avstånd sträcks så skärmen fylls, och texten blir något större.
        val scale = maxWidth.value / 1024f
        val bars = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value +
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val refAvailable = (maxHeight.value - bars) / scale - 136f
        // Mobil: text och höjder ~45 % större än i 2:3-referensen, fler skärmar scrollar.
        val stretch = (refAvailable / 1330f).coerceIn(1.45f, 1.6f)
        val font = stretch
        CompositionLocalProvider(
            LocalRefScale provides scale,
            LocalRefStretch provides stretch,
            LocalRefFont provides font
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Box(Modifier.weight(1f)) {
                    if (recording) {
                        RecordScreen(vm = vm, onExit = { recording = false })
                    } else {
                        when (tab) {
                            Tab.HOME -> HomeScreen(
                                vm = vm,
                                recent = if (library.isEmpty()) DemoEntries else library.map { it.toEntry() },
                                onStart = { recording = true },
                                onOpenPremium = { showPremium = true },
                                onOpenGallery = { tab = Tab.GALLERY },
                                onOpenSettings = { tab = Tab.SETTINGS },
                                onOpenTips = { showTips = true },
                                onOpenAbout = { showAbout = true },
                                onOpenEntry = openEntry
                            )
                            Tab.GALLERY -> GalleryScreen(library, openEntry, soon) { showAbout = true }
                            Tab.STATS -> StatsScreen(library, vm.isPremium, soon) { showPremium = true }
                            Tab.SETTINGS -> SettingsScreen(vm, onOpenAbout = { showAbout = true }, onOpenPremium = { showPremium = true })
                        }
                    }
                }
                if (!recording) BottomBar(current = tab, onSelect = { tab = it })
            }
        }
    }

    openItem?.let { item ->
        MediaDialog(
            item = item,
            onClose = { openItem = null },
            onShare = { shareItem(context, item) },
            onDelete = { pendingDelete = item; openItem = null }
        )
    }
    pendingDelete?.let { item ->
        ConfirmDelete(
            item = item,
            onConfirm = {
                val ok = deleteItem(context, item)
                pendingDelete = null
                Toast.makeText(context, if (ok) "Deleted" else "Could not delete", Toast.LENGTH_SHORT).show()
                reload++
            },
            onCancel = { pendingDelete = null }
        )
    }
    if (showPremium) {
        PremiumDialog(
            feature = "DualShots Premium",
            onDismiss = { showPremium = false },
            onUpgrade = { vm.grantPremium(); showPremium = false }
        )
    }
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            containerColor = DuelSurface2,
            title = { Text("DualShots", color = DuelText, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Version ${BuildConfig.VERSION_NAME} · Dual lens recording engine\nMade by nRnWorld · nrnworld.one\nbynrnworld@gmail.com",
                    color = DuelTextMuted, fontFamily = FontFamily.Monospace, fontSize = 12.sp
                )
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("OK", color = DuelOrange) } }
        )
    }
    if (showTips) {
        AlertDialog(
            onDismissRequest = { showTips = false },
            containerColor = DuelSurface2,
            title = { Text("Tips", color = DuelText, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "• Two cameras, one take – record front and back at once.\n\n" +
                        "• Drag, resize, pause or hide the small camera while you film. A paused camera turns black.\n\n" +
                        "• Tap the camera button to take a photo, with or without recording.\n\n" +
                        "• Settings → Saved video: pick 16:9 for YouTube even if the cameras are 9:16.",
                    color = DuelTextMuted, fontSize = 13.sp
                )
            },
            confirmButton = { TextButton(onClick = { showTips = false }) { Text("Got it", color = DuelOrange) } }
        )
    }
}

/** Fyra flikar som i Stats-referensen: mörk pill + orange för vald flik. */
@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF080B11))) {
        Box(Modifier.fillMaxWidth().height(rp(2)).background(Color(0xFF161C28)))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(rp(136)).padding(horizontal = rp(26))) {
            Tab.values().forEach { t ->
                val active = t == current
                val tint = if (active) RefOrange else Color(0xFF8A93A8)
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null
                    ) { onSelect(t) }
                ) {
                    Box(
                        Modifier.align(Alignment.TopCenter).absoluteOffset(y = rp(14)).size(rp(167), rp(61))
                            .clip(RoundedCornerShape(rp(24))).background(if (active) Color(0xFF3A2213) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) { RefIcon(t.icon, tint, rp(44), stroke = 1.7f) }
                    Text(
                        t.label, color = tint, fontSize = rs(25), lineHeight = rs(30), fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.TopCenter).absoluteOffset(y = rp(78))
                    )
                }
            }
        }
    }
}
