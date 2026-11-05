package com.nrworld.duelshot.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.nrworld.duelshot.model.AspectOption
import com.nrworld.duelshot.model.CodecOption
import com.nrworld.duelshot.model.QualityOption
import com.nrworld.duelshot.model.RecordingMode
import com.nrworld.duelshot.ui.AppViewModel
import com.nrworld.duelshot.ui.components.Brand
import com.nrworld.duelshot.ui.components.PremiumDialog
import com.nrworld.duelshot.ui.components.RCircle
import com.nrworld.duelshot.ui.components.RText
import com.nrworld.duelshot.ui.components.RefCard
import com.nrworld.duelshot.ui.components.RefCardBorder
import com.nrworld.duelshot.ui.components.RefCrown
import com.nrworld.duelshot.ui.components.RefFaint
import com.nrworld.duelshot.ui.components.RefGold
import com.nrworld.duelshot.ui.components.RefIcon
import com.nrworld.duelshot.ui.components.RefIconKind
import com.nrworld.duelshot.ui.components.RefLabel
import com.nrworld.duelshot.ui.components.RefMuted
import com.nrworld.duelshot.ui.components.RefOrange
import com.nrworld.duelshot.ui.components.RefSoft
import com.nrworld.duelshot.ui.components.RefSub
import com.nrworld.duelshot.ui.components.rp
import com.nrworld.duelshot.ui.components.rs

/** Settings enligt design/reference/settings.png, plus raden för filformat. */
@Composable
fun SettingsScreen(vm: AppViewModel, onOpenAbout: () -> Unit, onOpenPremium: () -> Unit) {
    var premiumFeature by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(rp(131))) {
            Brand(63, 11, 53)
            RText("CUSTOMIZE YOUR EXPERIENCE", 63, 67, 18, 24, color = RefFaint, mono = true, ls = 3.1)
            RText(
                buildAnnotatedString {
                    append("SETTINGS\n")
                    withStyle(SpanStyle(color = RefFaint)) { append("V1.1") }
                },
                149, 27, 15, 23, color = RefLabel, mono = true, ls = 3, right = true
            )
            RCircle(RefIconKind.GEAR, 902, 14, 70, 38, onClick = onOpenAbout)
        }

        Section("RECORDING MODE", hint = true, top = 0)
        Row(
            Modifier.padding(start = rp(63), end = rp(53), top = rp(12)).fillMaxWidth().height(rp(159)),
            horizontalArrangement = Arrangement.spacedBy(rp(18))
        ) {
            ModeCard(RecordingMode.DUAL_VIEW, RefIconKind.VIDEO, "Both cameras\nin one video", vm)
            ModeCard(RecordingMode.SEPARATE, RefIconKind.SEPARATE, "Two files, one\nfor each", vm)
            ModeCard(RecordingMode.BOTH, RefIconKind.OVERLAP, "Combined +\ntwo separate files", vm)
        }

        Section("CAMERA FORMATS", top = 27)
        Row(
            Modifier.padding(start = rp(63), end = rp(53), top = rp(11)).fillMaxWidth().height(rp(159)),
            horizontalArrangement = Arrangement.spacedBy(rp(18))
        ) {
            CameraCard("Front Camera", RefIconKind.USER, vm.frontAspect, Modifier.weight(444f)) { vm.frontAspect = it }
            CameraCard("Back Camera", RefIconKind.CAMERA, vm.backAspect, Modifier.weight(446f)) { vm.backAspect = it }
        }

        Section("SAVED VIDEO", top = 26, customHint = "DUAL VIEW FILE. " to "16:9 FOR YOUTUBE.")
        val formats = listOf<AspectOption?>(null, AspectOption.R_9_16, AspectOption.R_16_9, AspectOption.R_1_1)
        SegRow(
            icon = { RefIcon(RefIconKind.FILM, RefOrange, rp(42), Modifier.absoluteOffset(rp(25), rp(21))) },
            title = "Video format", sub = "Shape of the combined file", height = 86,
            options = listOf("Auto", "9:16", "16:9", "1:1"),
            selected = formats.indexOf(vm.saveAspect).coerceAtLeast(0),
            segX = 500, segW = 380, chevron = false
        ) { vm.saveAspect = formats[it] }

        Section("VIDEO QUALITY", top = 22, customHint = "HIGHER QUALITY. BETTER STORIES." to "")
        SegRow(
            icon = {
                Box(
                    Modifier.absoluteOffset(rp(25), rp(23)).size(rp(44), rp(38)).border(rp(3), RefOrange, RoundedCornerShape(rp(7))),
                    contentAlignment = Alignment.Center
                ) { Text("HD", color = RefOrange, fontSize = rs(17), fontWeight = FontWeight.Bold) }
            },
            title = "Resolution", sub = "Choose video resolution", height = 86,
            options = listOf("720p", "1080p", "4K"),
            selected = vm.quality.ordinal, segX = 548, segW = 296, chevron = true
        ) { idx ->
            val q = QualityOption.values()[idx]
            if (q.premium && !vm.isPremium) premiumFeature = q.label else vm.quality = q
        }

        Section("CONTAINER / CODEC", top = 22)
        SegRow(
            icon = { RefIcon(RefIconKind.FILE, RefOrange, rp(42), Modifier.absoluteOffset(rp(25), rp(23))) },
            title = "Format", sub = "Choose file format", height = 91,
            options = listOf("MP4", "HEVC"),
            selected = vm.codec.ordinal, segX = 632, segW = 206, chevron = true
        ) { idx ->
            val c = CodecOption.values()[idx]
            if (c.premium && !vm.isPremium) premiumFeature = c.label else vm.codec = c
        }

        Section("ADDITIONAL SETTINGS", top = 22)
        Column(
            Modifier.padding(start = rp(63), end = rp(53), top = rp(9)).fillMaxWidth()
                .clip(RoundedCornerShape(rp(22))).background(RefCard).border(rp(2), RefCardBorder, RoundedCornerShape(rp(22)))
                .padding(vertical = rp(3))
        ) {
            ToggleRow(RefIconKind.STABILIZE, "Stabilization", "Reduce camera shake", vm.stabilize, true) { vm.stabilize = it }
            ToggleRow(RefIconKind.GRID4, "Grid", "Show grid while recording", vm.grid, true) { vm.grid = it }
            ToggleRow(RefIconKind.AUDIO, "Record Audio", "Include audio from both cameras", vm.recordAudio, true) { vm.recordAudio = it }
            ToggleRow(RefIconKind.PIN, "Save Location", "Add location info to your videos", vm.saveLocation, false) { vm.saveLocation = it }
        }

        Box(
            Modifier.padding(start = rp(63), end = rp(53), top = rp(26)).fillMaxWidth().height(rp(94))
                .clip(RoundedCornerShape(rp(22)))
                .background(Brush.horizontalGradient(listOf(Color(0xFF24160D), Color(0xFF1A120E), Color(0xFF1D130E))))
                .border(rp(2), Color(0xFF5A2E12), RoundedCornerShape(rp(22)))
        ) {
            RefIcon(RefIconKind.CROWN, RefCrown, rp(48), Modifier.absoluteOffset(rp(31), rp(23)), stroke = 2f)
            RText("Premium Features", 116, 18, 25, 30, color = RefGold, weight = FontWeight.Medium)
            RText("Unlock 4K, 60fps, more formats and more.", 116, 52, 18, 22, color = RefSub)
            if (vm.isPremium) {
                RText("Premium active", 40, 30, 22, 30, color = RefGold, right = true)
            } else {
                Row(
                    Modifier.absoluteOffset(rp(659), rp(16)).size(rp(229), rp(58)).clip(RoundedCornerShape(rp(18)))
                        .border(rp(3), RefOrange, RoundedCornerShape(rp(18))).clickable(onClick = onOpenPremium)
                        .padding(start = rp(36), end = rp(26)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("GO PREMIUM", color = RefOrange, fontSize = rs(21), fontWeight = FontWeight.Medium, letterSpacing = rs(.5), maxLines = 1, softWrap = false)
                    RefIcon(RefIconKind.CHEVRON, RefOrange, rp(24), stroke = 2.6f)
                }
            }
        }
        Spacer(Modifier.height(rp(40)))
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
private fun Section(label: String, top: Int, hint: Boolean = false, customHint: Pair<String, String>? = null) {
    Row(
        Modifier.padding(start = rp(63), end = rp(53), top = rp(top)).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, color = RefLabel, fontSize = rs(21), lineHeight = rs(28), fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace, letterSpacing = rs(2.1), modifier = Modifier.weight(1f)
        )
        val h = customHint ?: if (hint) "RECORD BOTH SIDES. " to "AT ONCE." else null
        if (h != null) Text(
            buildAnnotatedString {
                append(h.first)
                withStyle(SpanStyle(color = RefOrange)) { append(h.second) }
            },
            color = RefFaint, fontSize = rs(15), fontFamily = FontFamily.Monospace, letterSpacing = rs(1.6), maxLines = 1
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ModeCard(mode: RecordingMode, icon: RefIconKind, sub: String, vm: AppViewModel) {
    val sel = vm.mode == mode
    val shape = RoundedCornerShape(rp(22))
    Box(
        Modifier.weight(1f).fillMaxHeight()
            .then(if (sel) Modifier.shadow(rp(18), shape, spotColor = RefOrange, ambientColor = RefOrange) else Modifier)
            .clip(shape)
            .background(if (sel) Brush.verticalGradient(listOf(Color(0xFF1D1410), Color(0xFF16120F))) else Brush.verticalGradient(listOf(RefCard, RefCard)))
            .border(rp(if (sel) 3 else 2), if (sel) RefOrange else RefCardBorder, shape)
            .clickable { vm.mode = mode }
    ) {
        RefIcon(icon, if (sel) RefOrange else Color(0xFFE3E8F0), rp(50), Modifier.absoluteOffset(rp(25), rp(39)))
        RText(mode.title, 101, 54, 21, 26, weight = FontWeight.Medium)
        RText(sub, 101, 87, 17.5, 24, color = RefMuted)
        Box(
            Modifier.align(Alignment.TopEnd).absoluteOffset(x = -rp(21), y = rp(19)).size(rp(30)).clip(CircleShape)
                .border(rp(if (sel) 3 else 2.5), if (sel) RefOrange else Color(0xFF4A5368), CircleShape),
            contentAlignment = Alignment.Center
        ) { if (sel) Box(Modifier.size(rp(15)).clip(CircleShape).background(RefOrange)) }
    }
}

@Composable
private fun CameraCard(title: String, icon: RefIconKind, value: AspectOption, modifier: Modifier, onPick: (AspectOption) -> Unit) {
    Box(modifier.fillMaxHeight().clip(RoundedCornerShape(rp(22))).background(RefCard).border(rp(2), RefCardBorder, RoundedCornerShape(rp(22)))) {
        RefIcon(icon, RefOrange, rp(42), Modifier.absoluteOffset(rp(25), rp(15)))
        RText(title, 96, 23, 21, 26, weight = FontWeight.Medium)
        Row(
            Modifier.absoluteOffset(rp(17), rp(71)).padding(end = rp(34)).fillMaxWidth().height(rp(69)),
            horizontalArrangement = Arrangement.spacedBy(rp(14))
        ) {
            listOf(AspectOption.R_9_16, AspectOption.R_16_9, AspectOption.R_1_1).forEach { a ->
                val active = a == value
                val (short, word) = a.label.split(' ').let { it[0] to it.getOrElse(1) { "" } }
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(rp(14)))
                        .background(if (active) Color(0xFF2A1A12) else Color(0xFF161C2A))
                        .border(rp(if (active) 3 else 2), if (active) RefOrange else Color(0xFF232B3C), RoundedCornerShape(rp(14)))
                        .clickable { onPick(a) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(short, color = if (active) Color.White else RefSoft, fontSize = rs(22), lineHeight = rs(26), fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                    Text(word, color = if (active) Color(0xFFE3E8F0) else RefMuted, fontSize = rs(18), lineHeight = rs(22))
                }
            }
        }
    }
}

@Composable
private fun SegRow(
    icon: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
    title: String, sub: String, height: Int,
    options: List<String>, selected: Int, segX: Int, segW: Int, chevron: Boolean,
    onSelect: (Int) -> Unit
) {
    Box(
        Modifier.padding(start = rp(63), end = rp(53), top = rp(11)).fillMaxWidth().height(rp(height))
            .clip(RoundedCornerShape(rp(22))).background(RefCard).border(rp(2), RefCardBorder, RoundedCornerShape(rp(22)))
    ) {
        icon()
        val dy = if (height > 88) 2 else 0
        RText(title, 105, 13 + dy, 21, 26, weight = FontWeight.Medium)
        RText(sub, 105, 44 + dy, 18, 22, color = RefSub)
        Row(
            Modifier.absoluteOffset(rp(segX), rp(15 + dy)).size(rp(segW), rp(51 + dy)).clip(RoundedCornerShape(rp(14)))
                .background(Color(0xFF1A2131)).border(rp(1.5), Color(0xFF232B3C), RoundedCornerShape(rp(14))).padding(rp(2))
        ) {
            options.forEachIndexed { i, label ->
                val active = i == selected
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(rp(12)))
                        .background(if (active) Brush.horizontalGradient(listOf(RefOrange, Color(0xFFFF8A1C))) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, color = if (active) Color(0xFF2A1206) else RefSub, fontSize = rs(21), fontWeight = if (active) FontWeight.Medium else FontWeight.Normal)
                }
            }
        }
        if (chevron) RefIcon(RefIconKind.CHEVRON, RefSub, rp(28), Modifier.absoluteOffset(rp(864), rp(27 + dy)), stroke = 2f)
    }
}

@Composable
private fun ToggleRow(icon: RefIconKind, title: String, sub: String, on: Boolean, divider: Boolean, onChange: (Boolean) -> Unit) {
    Box(Modifier.fillMaxWidth().height(rp(70)).clickable { onChange(!on) }) {
        RefIcon(icon, RefOrange, rp(44), Modifier.absoluteOffset(rp(28), rp(12)))
        RText(title, 105, 8, 21, 26, weight = FontWeight.Medium)
        RText(sub, 105, 37, 18, 22, color = RefSub)
        val knob by animateDpAsState(if (on) rp(27) else rp(1), label = "knob")
        Box(
            Modifier.absoluteOffset(rp(812), rp(14)).size(rp(64), rp(38)).clip(RoundedCornerShape(rp(19)))
                .background(if (on) RefOrange else Color(0xFF1D2333))
                .border(rp(2), if (on) RefOrange else Color(0xFF2A3245), RoundedCornerShape(rp(19)))
        ) {
            Box(Modifier.absoluteOffset(knob, rp(1)).size(rp(32)).clip(CircleShape).background(Color.White))
        }
        if (divider) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(rp(2)).background(Color(0xFF1A2132)))
    }
}

