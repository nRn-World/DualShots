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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.nrworld.duelshot.ui.theme.DuelLine
import com.nrworld.duelshot.ui.theme.DuelRecord
import com.nrworld.duelshot.ui.theme.DuelSurface
import com.nrworld.duelshot.ui.theme.DuelText
import com.nrworld.duelshot.ui.theme.DuelTextMuted
import kotlinx.coroutines.delay

@Composable
fun RecordScreen(vm: AppViewModel, onExit: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { DualCameraController(context) }

    var backView by remember { mutableStateOf<PreviewView?>(null) }
    var frontView by remember { mutableStateOf<PreviewView?>(null) }
    var composedView by remember { mutableStateOf<PreviewView?>(null) }

    var status by remember { mutableStateOf("Startar kameror…") }
    var camReady by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var renderedMode by remember { mutableStateOf(vm.mode) }
    var isRecording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0) }
    var savedNames by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        status = controller.init()
        camReady = controller.canBind
    }

    // Bind när förhandsvisningarna finns (kör om vid inställningsändring).
    LaunchedEffect(camReady, ready, backView, frontView, composedView, vm.mode, vm.frontAspect, vm.backAspect, vm.quality) {
        if (!camReady) return@LaunchedEffect
        if (controller.supported) {
            if (vm.mode == RecordingMode.DUAL_VIEW) {
                if (composedView == null) return@LaunchedEffect
            } else if (backView == null || frontView == null) return@LaunchedEffect
        } else {
            if (backView == null && frontView == null) return@LaunchedEffect
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
        ready = true
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
            IconChip("✕") { if (isRecording) controller.stopRecording(); onExit() }
            Spacer(Modifier.width(12.dp))
            TimerPill(elapsed, isRecording)
            Spacer(Modifier.weight(1f))
            IconChip("⟳") { /* byt primär kamera – plats för framtida växling */ }
        }

        // Förhandsvisning
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
            if (!controller.supported) {
                // Enskild kamera: en fullmatad förhandsvisning.
                Feed(backView, label = "KAMERA", ratio = vm.backAspect.label, modifier = Modifier.fillMaxSize()) { backView = it }
            } else {
                when (renderedMode) {
                    RecordingMode.DUAL_VIEW -> Feed(composedView) { composedView = it }
                    else -> Row(modifier = Modifier.fillMaxSize()) {
                        Feed(backView, label = "BAK", ratio = vm.backAspect.label, modifier = Modifier.weight(1f)) { backView = it }
                        Spacer(Modifier.width(6.dp))
                        Feed(frontView, label = "FRAM", ratio = vm.frontAspect.label, modifier = Modifier.weight(1f)) { frontView = it }
                    }
                }
                // Dold yta som behövs för composition i BOTH-läget.
                if (renderedMode == RecordingMode.BOTH) {
                    AndroidView(
                        modifier = Modifier.size(1.dp),
                        factory = { ctx -> PreviewView(ctx).also { composedView = it } }
                    )
                }
            }
        }

        // Status + sparade filer
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(status, color = DuelTextMuted, fontSize = 12.sp)
            if (savedNames.isNotEmpty()) {
                Text(
                    "Sparade: " + savedNames.joinToString(", "),
                    color = Color(0xFF8FA0C8), fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Kontrollrad
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RecordButton(
                recording = isRecording,
                enabled = ready && controller.canBind,
                onClick = {
                    if (!isRecording) {
                        controller.startRecording(
                            onStarted = { names -> savedNames = names; isRecording = true },
                            onError = { status = it }
                        )
                    } else {
                        controller.stopRecording()
                        isRecording = false
                        status = "Stoppad. Sparad i Galleri → Movies/DuelShots"
                    }
                }
            )
        }
    }
}

@Composable
private fun Feed(
    view: PreviewView?,
    label: String? = null,
    ratio: String? = null,
    modifier: Modifier = Modifier,
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
            }
        )
        if (label != null) {
            Text(
                label, color = DuelText, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                    .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = .45f)).padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        if (ratio != null) {
            Text(
                ratio, color = Color(0xFFCFE6FF), fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = .45f)).padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun IconChip(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
            .background(DuelSurface).border(1.dp, DuelLine, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = DuelTextMuted, fontSize = 16.sp)
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
        modifier = Modifier.size(74.dp).clip(CircleShape)
            .background(if (enabled) Color.White else Color.Gray.copy(alpha = .3f))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(if (recording) 30.dp else 56.dp)
                .clip(if (recording) RoundedCornerShape(6.dp) else CircleShape)
                .background(DuelRecord)
        )
    }
}
