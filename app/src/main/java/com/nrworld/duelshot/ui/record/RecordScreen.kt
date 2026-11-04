package com.nrworld.duelshot.ui.record

import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nrworld.duelshot.camera.DualCameraController
import com.nrworld.duelshot.model.RecordingMode
import com.nrworld.duelshot.ui.AppViewModel
import com.nrworld.duelshot.ui.components.DuelIcon
import com.nrworld.duelshot.ui.components.DuelIconKind
import com.nrworld.duelshot.ui.theme.DuelLine
import com.nrworld.duelshot.ui.theme.DuelOrange
import com.nrworld.duelshot.ui.theme.DuelOrangeLight
import com.nrworld.duelshot.ui.theme.DuelRecord
import com.nrworld.duelshot.ui.theme.DuelSurface
import com.nrworld.duelshot.ui.theme.DuelSurface2
import com.nrworld.duelshot.ui.theme.DuelText
import com.nrworld.duelshot.ui.theme.DuelTextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import com.nrworld.duelshot.model.AspectOption
import com.nrworld.duelshot.model.QualityOption
import com.nrworld.duelshot.ui.components.PremiumDialog
import com.nrworld.duelshot.ui.components.SegmentedControl

@Composable
fun RecordScreen(vm: AppViewModel, onExit: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { DualCameraController(context) }

    var backView by remember { mutableStateOf<PreviewView?>(null) }
    var frontView by remember { mutableStateOf<PreviewView?>(null) }
    var composedView by remember { mutableStateOf<PreviewView?>(null) }

    var status by remember { mutableStateOf("Starting cameras…") }
    var camReady by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var renderedMode by remember { mutableStateOf(vm.mode) }
    var boundFor by remember { mutableStateOf<RecordingMode?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0) }
    var savedNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var part by remember { mutableStateOf(1) }
    var showTune by remember { mutableStateOf(false) }
    var stacked by remember { mutableStateOf(false) }
    var premiumFeature by remember { mutableStateOf<String?>(null) }

    // Visat läge: önskat läge tills bindningen skett, därefter det som faktiskt band (fallback).
    val displayMode = if (boundFor == vm.mode) renderedMode else vm.mode

    LaunchedEffect(Unit) {
        status = controller.init()
        camReady = controller.canBind
    }

    // Bind när förhandsvisningarna finns. Ändras något under inspelning avslutas klippet,
    // kameran binds om och nästa del (_p2, _p3 …) startar direkt.
    LaunchedEffect(camReady, ready, backView, frontView, composedView, vm.mode, vm.frontAspect, vm.backAspect, vm.quality) {
        if (!camReady) return@LaunchedEffect
        if (controller.supported) {
            when (vm.mode) {
                RecordingMode.DUAL_VIEW -> if (composedView == null) return@LaunchedEffect
                RecordingMode.SEPARATE -> if (backView == null || frontView == null) return@LaunchedEffect
                RecordingMode.BOTH -> if (backView == null || frontView == null || composedView == null) return@LaunchedEffect
            }
        } else if (backView == null && frontView == null) return@LaunchedEffect

        val resume = isRecording
        if (resume) {
            withTimeoutOrNull(4000) {
                suspendCancellableCoroutine<Unit> { c -> controller.stopRecording { if (c.isActive) c.resume(Unit) } }
            }
        }
        status = controller.bind(
            wanted = vm.mode,
            frontAspect = vm.frontAspect,
            backAspect = vm.backAspect,
            quality = vm.quality,
            lifecycleOwner = lifecycleOwner,
            backView = backView,
            frontView = frontView,
            composedView = composedView
        )
        renderedMode = controller.activeMode
        boundFor = vm.mode
        ready = true
        if (resume) {
            part++
            controller.startRecording(
                part = part,
                onStarted = { names -> savedNames = savedNames + names },
                onError = { status = it; isRecording = false }
            )
        }
    }

    // Timer.
    LaunchedEffect(isRecording) {
        if (isRecording) {
            elapsed = 0
            while (isRecording) { delay(1000); elapsed++ }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Toppfält
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconChip(DuelIconKind.CLOSE) { if (isRecording) controller.stopRecording(); onExit() }
            Spacer(Modifier.width(12.dp))
            TimerPill(elapsed, isRecording)
            Spacer(Modifier.weight(1f))
            IconChip(DuelIconKind.SETTINGS, active = showTune) { showTune = !showTune }
        }

        // Förhandsvisning
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
            if (!controller.supported) {
                // Enskild kamera: en fullmatad förhandsvisning i valt format.
                FramedFeed(
                    backView, vm.backAspect, label = "CAMERA", modifier = Modifier.fillMaxSize(),
                    onRelease = { if (backView === it) backView = null }
                ) { backView = it }
            } else {
                if (displayMode == RecordingMode.DUAL_VIEW) {
                    Feed(
                        composedView, modifier = Modifier.fillMaxSize(),
                        onRelease = { if (composedView === it) composedView = null }
                    ) { composedView = it }
                } else {
                    // Samma två vyer oavsett layout – bara storlek/placering ändras (ingen omstart).
                    val cellA = if (stacked) Modifier.fillMaxWidth().fillMaxHeight(0.5f).align(Alignment.TopCenter)
                    else Modifier.fillMaxHeight().fillMaxWidth(0.5f).align(Alignment.CenterStart)
                    val cellB = if (stacked) Modifier.fillMaxWidth().fillMaxHeight(0.5f).align(Alignment.BottomCenter)
                    else Modifier.fillMaxHeight().fillMaxWidth(0.5f).align(Alignment.CenterEnd)
                    FramedFeed(
                        backView, vm.backAspect, label = "BACK", modifier = cellA.padding(3.dp),
                        onRelease = { if (backView === it) backView = null }
                    ) { backView = it }
                    FramedFeed(
                        frontView, vm.frontAspect, label = "FRONT", modifier = cellB.padding(3.dp),
                        onRelease = { if (frontView === it) frontView = null }
                    ) { frontView = it }
                }
                // Dold yta som behövs för composition i BOTH-läget.
                if (displayMode == RecordingMode.BOTH) {
                    AndroidView(
                        modifier = Modifier.size(1.dp),
                        factory = { ctx -> PreviewView(ctx).also { composedView = it } },
                        onRelease = { if (composedView === it) composedView = null }
                    )
                }
            }
        }

        // Ändra live: läge, kvalitet, format och layout.
        if (showTune) {
            val dual = controller.supported
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isRecording) {
                    Text("Changes start a new clip part (_p2, _p3 …)", color = DuelOrangeLight, fontSize = 11.sp)
                }
                if (dual) TuneRow("MODE") {
                    SegmentedControl(
                        options = listOf("Dual", "Separate", "Both"),
                        selectedIndex = vm.mode.ordinal,
                        onSelect = { vm.mode = RecordingMode.values()[it] }
                    )
                }
                TuneRow("QUALITY") {
                    SegmentedControl(
                        options = listOf("720p", "1080p", "4K"),
                        selectedIndex = vm.quality.ordinal,
                        onSelect = { idx ->
                            val q = QualityOption.values()[idx]
                            if (q.premium && !vm.isPremium) premiumFeature = q.label else vm.quality = q
                        }
                    )
                }
                TuneRow("BACK") {
                    SegmentedControl(
                        options = AspectOption.values().map { it.label.substringBefore(' ') },
                        selectedIndex = vm.backAspect.ordinal,
                        onSelect = { vm.backAspect = AspectOption.values()[it] }
                    )
                }
                if (dual) {
                    TuneRow("FRONT") {
                        SegmentedControl(
                            options = AspectOption.values().map { it.label.substringBefore(' ') },
                            selectedIndex = vm.frontAspect.ordinal,
                            onSelect = { vm.frontAspect = AspectOption.values()[it] }
                        )
                    }
                    if (displayMode != RecordingMode.DUAL_VIEW) TuneRow("LAYOUT") {
                        SegmentedControl(
                            options = listOf("Side", "Stack"),
                            selectedIndex = if (stacked) 1 else 0,
                            onSelect = { stacked = it == 1 }
                        )
                    }
                }
            }
        }

        // Status + sparade filer
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(status, color = DuelTextMuted, fontSize = 12.sp)
            if (savedNames.isNotEmpty()) {
                Text(
                    "Saved: " + savedNames.joinToString(", "),
                    color = Color(0xFF8FA0C8), fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Kontrollrad
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 22.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RecordButton(
                recording = isRecording,
                enabled = ready && controller.canBind,
                onClick = {
                    if (!isRecording) {
                        part = 1
                        controller.startRecording(
                            onStarted = { names -> savedNames = names; isRecording = true },
                            onError = { status = it }
                        )
                    } else {
                        controller.stopRecording()
                        isRecording = false
                        status = "Stopped. Saved to Gallery → Movies/DuelShots"
                    }
                }
            )
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
private fun TuneRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label, color = DuelTextMuted, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp,
            modifier = Modifier.width(64.dp)
        )
        content()
    }
}

private fun AspectOption.ratio(): Float = when (this) {
    AspectOption.R_9_16 -> 9f / 16f
    AspectOption.R_16_9 -> 16f / 9f
    AspectOption.R_1_1 -> 1f
    AspectOption.R_4_3 -> 4f / 3f
}

/** Förhandsvisning i exakt valt bildformat, centrerad i sin cell. */
@Composable
private fun FramedFeed(
    view: PreviewView?,
    aspect: AspectOption,
    label: String,
    modifier: Modifier = Modifier,
    onRelease: (PreviewView) -> Unit,
    onView: (PreviewView) -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Feed(
            view, label = label, ratio = aspect.label,
            modifier = Modifier.aspectRatio(aspect.ratio()),
            onRelease = onRelease, onView = onView
        )
    }
}

@Composable
private fun Feed(
    view: PreviewView?,
    label: String? = null,
    ratio: String? = null,
    modifier: Modifier = Modifier,
    onRelease: (PreviewView) -> Unit = {},
    onView: (PreviewView) -> Unit
) {
    Box(modifier = modifier.clip(RoundedCornerShape(14.dp)).background(DuelSurface).border(1.dp, DuelLine, RoundedCornerShape(14.dp))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    onView(this)
                }
            },
            onRelease = onRelease
        )
        if (label != null) {
            Text(
                label, color = DuelOrangeLight, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                    .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = .45f)).padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        if (ratio != null) {
            Text(
                ratio, color = DuelText, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = .45f)).padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun IconChip(kind: DuelIconKind, active: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(42.dp).clip(CircleShape)
            .background(DuelSurface2).border(1.dp, if (active) DuelOrange else DuelLine, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        DuelIcon(kind, DuelText, 18.dp)
    }
}

@Composable
private fun TimerPill(seconds: Int, recording: Boolean) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (recording) DuelRecord.copy(alpha = .16f) else DuelSurface)
            .border(1.dp, if (recording) DuelRecord.copy(alpha = .5f) else DuelLine, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (recording) DuelRecord else DuelTextMuted))
        Spacer(Modifier.width(8.dp))
        Text(
            "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60),
            color = DuelText, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 13.sp
        )
    }
}

@Composable
private fun RecordButton(recording: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(88.dp).clip(CircleShape)
            .border(3.dp, if (enabled) Color.White.copy(alpha = .9f) else Color.Gray.copy(alpha = .3f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(if (recording) 30.dp else 66.dp)
                .clip(if (recording) RoundedCornerShape(8.dp) else CircleShape)
                .background(if (enabled) DuelRecord else Color.Gray.copy(alpha = .5f))
        )
    }
}
