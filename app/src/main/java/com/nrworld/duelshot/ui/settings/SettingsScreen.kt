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
import com.nrworld.duelshot.BuildConfig
import com.nrworld.duelshot.model.AspectOption
import com.nrworld.duelshot.model.CodecOption
import com.nrworld.duelshot.model.QualityOption
import com.nrworld.duelshot.model.RecordingMode
import com.nrworld.duelshot.ui.AppViewModel
import com.nrworld.duelshot.ui.components.Brand
import com.nrworld.duelshot.ui.components.RCircle
import com.nrworld.duelshot.ui.components.RText
import com.nrworld.duelshot.ui.components.RefCard
import com.nrworld.duelshot.ui.components.RefCardBorder
import com.nrworld.duelshot.ui.components.RefFaint
import com.nrworld.duelshot.ui.components.RefIcon
import com.nrworld.duelshot.ui.components.RefIconKind
import com.nrworld.duelshot.ui.components.RefLabel
import com.nrworld.duelshot.ui.components.RefMuted
import com.nrworld.duelshot.ui.components.RefOrange
import com.nrworld.duelshot.ui.components.RefSoft
import com.nrworld.duelshot.ui.components.RefSub
import com.nrworld.duelshot.ui.components.rp
import com.nrworld.duelshot.ui.components.rv
import com.nrworld.duelshot.ui.components.rs

/** Settings enligt design/reference/settings.png, plus raden för filformat. */
@Composable
fun SettingsScreen(vm: AppViewModel, onOpenAbout: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(rv(131))) {
            Brand(63, 11, 53)
            RText("CUSTOMIZE YOUR EXPERIENCE", 63, 67, 18, 24, color = RefFaint, mono = true, ls = 3.1)
            RText(
                buildAnnotatedString {
                    append("SETTINGS\n")
                    withStyle(SpanStyle(color = RefFaint)) { append("V" + BuildConfig.VERSION_NAME) }
                },
                149, 27, 15, 23, color = RefLabel, mono = true, ls = 3, right = true
            )
            RCircle(RefIconKind.GEAR, 902, 14, 70, 38, onClick = onOpenAbout)
        }

        Section("RECORDING MODE", hint = true, top = 0)
        Column(
            Modifier.padding(start = rp(63), end = rp(53), top = rv(12)).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(rv(14))
        ) {
            ModeCard(RecordingMode.DUAL_VIEW, RefIconKind.VIDEO, "Both cameras in one video", vm) { vm.mode = it }
            ModeCard(RecordingMode.SEPARATE, RefIconKind.SEPARATE, "Two files, one for each", vm) { vm.mode = it }
            ModeCard(RecordingMode.BOTH, RefIconKind.OVERLAP, "Combined + two separate files", vm) { vm.mode = it }
        }

        Section("CAMERA FORMATS", top = 27)
        Column(
            Modifier.padding(start = rp(63), end = rp(53), top = rv(11)).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(rv(14))
        ) {
            CameraCard("Front Camera", RefIconKind.USER, vm.frontAspect, Modifier.fillMaxWidth().height(rv(168))) { vm.frontAspect = it }
            CameraCard("Back Camera", RefIconKind.CAMERA, vm.backAspect, Modifier.fillMaxWidth().height(rv(168))) { vm.backAspect = it }
        }

        Section("SAVED VIDEO", top = 26, customHint = "DUAL VIEW FILE. " to "16:9 FOR YOUTUBE.")
        val formats = listOf<AspectOption?>(null, AspectOption.R_9_16, AspectOption.R_16_9, AspectOption.R_1_1)
        SegRow(
            icon = { RefIcon(RefIconKind.FILM, RefOrange, rp(42), Modifier.absoluteOffset(rp(25), rv(21))) },
            title = "Video format", sub = "Shape of the combined file", height = 86,
            options = listOf("Auto", "9:16", "16:9", "1:1"),
            selected = formats.indexOf(vm.saveAspect).coerceAtLeast(0),
            segX = 500, segW = 380, chevron = false
        ) { idx ->
            vm.saveAspect = formats[idx]
        }

        Section("VIDEO QUALITY", top = 22, customHint = "HIGHER QUALITY. BETTER STORIES." to "")
        SegRow(
            icon = {
                Box(
                    Modifier.absoluteOffset(rp(25), rv(23)).size(rp(44), rv(38)).border(rp(3), RefOrange, RoundedCornerShape(rp(7))),
                    contentAlignment = Alignment.Center
                ) { Text("HD", color = RefOrange, fontSize = rs(17), fontWeight = FontWeight.Bold) }
            },
            title = "Resolution", sub = "Choose video resolution", height = 86,
            options = listOf("720p", "1080p", "4K"),
            selected = vm.quality.ordinal, segX = 548, segW = 296, chevron = true
        ) { idx ->
            vm.quality = QualityOption.values()[idx]
        }

        Section("CONTAINER / CODEC", top = 22)
        SegRow(
            icon = { RefIcon(RefIconKind.FILE, RefOrange, rp(42), Modifier.absoluteOffset(rp(25), rv(23))) },
            title = "Format", sub = "Choose file format", height = 91,
            options = listOf("MP4", "HEVC"),
            selected = vm.codec.ordinal, segX = 632, segW = 206, chevron = true
        ) { idx ->
            vm.codec = CodecOption.values()[idx]
        }

        Section("COUNTDOWN TIMER", top = 22)
        val timers = listOf(0, 3, 5)
        SegRow(
            icon = { RefIcon(RefIconKind.CALENDAR, RefOrange, rp(42), Modifier.absoluteOffset(rp(25), rv(23))) },
            title = "Timer", sub = "Delay before recording starts", height = 86,
            options = listOf("Off", "3s", "5s"),
            selected = timers.indexOf(vm.countdownSec).coerceAtLeast(0),
            segX = 560, segW = 320, chevron = false
        ) { idx ->
            vm.countdownSec = timers[idx]
        }

        Section("ADDITIONAL SETTINGS", top = 22)
        Column(
            Modifier.padding(start = rp(63), end = rp(53), top = rv(9)).fillMaxWidth()
                .clip(RoundedCornerShape(rp(22))).background(RefCard).border(rp(2), RefCardBorder, RoundedCornerShape(rp(22)))
                .padding(vertical = rv(3))
        ) {
            ToggleRow(RefIconKind.GRID4, "Grid", "Show 3×3 framing grid", vm.grid, true) { vm.grid = it }
            ToggleRow(RefIconKind.AUDIO, "Record Audio", "Capture microphone audio", vm.recordAudio, false) { vm.recordAudio = it }
        }
        Spacer(Modifier.height(rv(40)))
    }
}

@Composable
private fun Section(label: String, top: Int, hint: Boolean = false, customHint: Pair<String, String>? = null) {
    Row(
        Modifier.padding(start = rp(63), end = rp(53), top = rv(top)).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, color = RefLabel, fontSize = rs(18), lineHeight = rs(28), fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace, letterSpacing = rs(2.1), modifier = Modifier.weight(1f)
        )
        val h = customHint ?: if (hint) "RECORD BOTH SIDES. " to "AT ONCE." else null
        if (h != null) Text(
            buildAnnotatedString {
                append(h.first)
                withStyle(SpanStyle(color = RefOrange)) { append(h.second) }
            },
            color = RefFaint, fontSize = rs(12.5), fontFamily = FontFamily.Monospace, letterSpacing = rs(1.2), maxLines = 1
        )
    }
}

@Composable
private fun ModeCard(mode: RecordingMode, icon: RefIconKind, sub: String, vm: AppViewModel, onClick: (RecordingMode) -> Unit) {
    val sel = vm.mode == mode
    val shape = RoundedCornerShape(rp(22))
    Box(
        Modifier.fillMaxWidth().height(rv(116))
            .then(if (sel) Modifier.shadow(rp(18), shape, spotColor = RefOrange, ambientColor = RefOrange) else Modifier)
            .clip(shape)
            .background(if (sel) Brush.verticalGradient(listOf(Color(0xFF1D1410), Color(0xFF16120F))) else Brush.verticalGradient(listOf(RefCard, RefCard)))
            .border(rp(if (sel) 3 else 2), if (sel) RefOrange else RefCardBorder, shape)
            .clickable { onClick(mode) }
    ) {
        RefIcon(icon, if (sel) RefOrange else Color(0xFFE3E8F0), rp(54), Modifier.absoluteOffset(rp(28), rv(31)))
        RText(mode.title, 112, 22, 21, 30, weight = FontWeight.Medium, maxWidth = 620)
        RText(sub, 112, 64, 16, 24, color = RefMuted, maxWidth = 700)
        Box(
            Modifier.align(Alignment.TopEnd).absoluteOffset(x = -rp(28), y = rv(38)).size(rp(34)).clip(CircleShape)
                .border(rp(if (sel) 3 else 2.5), if (sel) RefOrange else Color(0xFF4A5368), CircleShape),
            contentAlignment = Alignment.Center
        ) { if (sel) Box(Modifier.size(rp(17)).clip(CircleShape).background(RefOrange)) }
    }
}

@Composable
private fun CameraCard(title: String, icon: RefIconKind, value: AspectOption, modifier: Modifier, onPick: (AspectOption) -> Unit) {
    Box(modifier.fillMaxHeight().clip(RoundedCornerShape(rp(22))).background(RefCard).border(rp(2), RefCardBorder, RoundedCornerShape(rp(22)))) {
        RefIcon(icon, RefOrange, rp(46), Modifier.absoluteOffset(rp(25), rv(16)))
        RText(title, 96, 22, 21, 30, weight = FontWeight.Medium)
        Row(
            Modifier.padding(start = rp(17), end = rp(17), top = rv(82)).fillMaxWidth().height(rv(66)),
            horizontalArrangement = Arrangement.spacedBy(rp(14))
        ) {
            listOf(AspectOption.R_9_16, AspectOption.R_16_9, AspectOption.R_1_1).forEach { a ->
                val active = a == value
                val short = a.label.substringBefore(' ')
                val word = when (a) {
                    AspectOption.R_9_16 -> "Reels"
                    AspectOption.R_16_9 -> "YouTube"
                    else -> "Feed"
                }
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(rp(14)))
                        .background(if (active) Color(0xFF2A1A12) else Color(0xFF161C2A))
                        .border(rp(if (active) 3 else 2), if (active) RefOrange else Color(0xFF232B3C), RoundedCornerShape(rp(14)))
                        .clickable { onPick(a) }
                ) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(short, color = if (active) Color.White else RefSoft, fontSize = rs(21), lineHeight = rs(26), fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                        Text(word, color = if (active) Color(0xFFE3E8F0) else RefMuted, fontSize = rs(16), lineHeight = rs(21), maxLines = 1, softWrap = false)
                    }
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
        Modifier.padding(start = rp(63), end = rp(53), top = rv(11)).fillMaxWidth().height(rv(height))
            .clip(RoundedCornerShape(rp(22))).background(RefCard).border(rp(2), RefCardBorder, RoundedCornerShape(rp(22)))
    ) {
        icon()
        val dy = if (height > 88) 2 else 0
        RText(title, 105, 13 + dy, 19, 26, weight = FontWeight.Medium)
        RText(sub, 105, 44 + dy, 14.5, 22, color = RefSub, maxWidth = 380)
        Row(
            Modifier.absoluteOffset(rp(segX), rv(15 + dy)).size(rp(segW), rv(51 + dy)).clip(RoundedCornerShape(rp(14)))
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
                    Text(label, color = if (active) Color(0xFF2A1206) else RefSub, fontSize = rs(18), fontWeight = if (active) FontWeight.Medium else FontWeight.Normal)
                }
            }
        }
        if (chevron) RefIcon(RefIconKind.CHEVRON, RefSub, rp(28), Modifier.absoluteOffset(rp(864), rv(27 + dy)), stroke = 2f)
    }
}

@Composable
private fun ToggleRow(icon: RefIconKind, title: String, sub: String, on: Boolean, divider: Boolean, onChange: (Boolean) -> Unit) {
    Box(Modifier.fillMaxWidth().height(rv(70)).clickable { onChange(!on) }) {
        RefIcon(icon, RefOrange, rp(44), Modifier.absoluteOffset(rp(28), rv(12)))
        RText(title, 105, 8, 19, 26, weight = FontWeight.Medium)
        RText(sub, 105, 37, 14.5, 22, color = RefSub, maxWidth = 640)
        val knob by animateDpAsState(if (on) rp(27) else rp(1), label = "knob")
        Box(
            Modifier.absoluteOffset(rp(812), rv(14)).size(rp(64), rv(38)).clip(RoundedCornerShape(rp(19)))
                .background(if (on) RefOrange else Color(0xFF1D2333))
                .border(rp(2), if (on) RefOrange else Color(0xFF2A3245), RoundedCornerShape(rp(19)))
        ) {
            Box(Modifier.absoluteOffset(knob, rp(1)).size(rp(32)).clip(CircleShape).background(Color.White))
        }
        if (divider) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(rv(2)).background(Color(0xFF1A2132)))
    }
}

