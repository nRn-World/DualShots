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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.nrworld.duelshot.ui.theme.DuelGold
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.nrworld.duelshot.camera.PipLayout
import com.nrworld.duelshot.camera.PhotoSaver
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RecordScreen(vm: AppViewModel, onExit: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val localView = LocalView.current
    val controller = remember { DualCameraController(context) }

    var backView by remember { mutableStateOf<PreviewView?>(null) }
    var frontView by remember { mutableStateOf<PreviewView?>(null) }
    var composedView by remember { mutableStateOf<PreviewView?>(null) }

    var status by remember { mutableStateOf("Starting cameras…") }
    var camReady by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var renderedMode by remember { mutableStateOf(vm.mode) }
    var boundFor by remember { mutableStateOf<RecordingMode?>(null) }
    // Satt när valt läge inte stöds och vi bundit om i ett enklare läge.
    var fallbackFrom by remember { mutableStateOf<RecordingMode?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0) }
    var savedNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var part by remember { mutableStateOf(1) }
    var showTune by remember { mutableStateOf(false) }
    var stacked by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(vm.grid) }
    var paused by remember { mutableStateOf(false) }
    // Stora kameran i Dual View kan pausas på egen hand (svart bild, samma fil).
    var mainPaused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var premiumFeature by remember { mutableStateOf<String?>(null) }
    // Bild-i-bild: [pip] är sparad placering, [pipLive] följer fingret.
    var pip by remember { mutableStateOf(PipLayout()) }
    var pipLive by remember { mutableStateOf(PipLayout()) }
    // Zoom per kamera; två fingrar ändrar och inspelningen följer med.
    var backZoom by remember { mutableFloatStateOf(1f) }
    var frontZoom by remember { mutableFloatStateOf(1f) }
    // Delningen mellan de två rutorna i Separate-läget (går att dra) samt
    // per-kamera-paus och bortklickning under inspelning.
    var split by remember { mutableFloatStateOf(0.5f) }
    var hideBack by remember { mutableStateOf(false) }
    var hideFront by remember { mutableStateOf(false) }
    var backPaused by remember { mutableStateOf(false) }
    var frontPaused by remember { mutableStateOf(false) }

    // Visat läge: önskat läge tills bindningen skett, därefter det som faktiskt band (fallback).
    val displayMode = if (boundFor == vm.mode) renderedMode else vm.mode

    // Skärmen ska inte slockna mitt i en inspelning.
    DisposableEffect(isRecording) {
        localView.keepScreenOn = isRecording
        onDispose { localView.keepScreenOn = false }
    }

    LaunchedEffect(Unit) {
        status = controller.init()
        camReady = controller.canBind
    }
    // Stäng kamerorna när man lämnar inspelningsvyn.
    DisposableEffect(Unit) { onDispose { controller.release() } }

    // Bind när förhandsvisningarna finns. Ändras något under inspelning avslutas klippet,
    // kameran binds om och nästa del (_p2, _p3 …) startar direkt.
    LaunchedEffect(
        camReady, ready, backView, frontView, composedView, vm.mode, vm.frontAspect,
        vm.backAspect, vm.quality, vm.saveAspect, pip, mainPaused, isRecording, fallbackFrom
    ) {
        if (!camReady) return@LaunchedEffect
        // En take = en fil: ändringar under inspelning väntar till nästa take.
        if (isRecording) return@LaunchedEffect
        // Dual View kan saknas på enheten. Bind då om i det läge som faktiskt
        // stöds i stället för att försöka igen – annars blir rutorna svarta.
        val wanted = if (fallbackFrom == vm.mode) renderedMode else vm.mode
        if (controller.supported) {
            when (wanted) {
                RecordingMode.DUAL_VIEW -> if (composedView == null) return@LaunchedEffect
                RecordingMode.SEPARATE -> if (backView == null || frontView == null) return@LaunchedEffect
                RecordingMode.BOTH -> if (backView == null || frontView == null || composedView == null) return@LaunchedEffect
            }
        } else if (backView == null && frontView == null) return@LaunchedEffect

        controller.recordAudio = vm.recordAudio
        controller.mainPaused = mainPaused
        status = controller.bind(
            wanted = wanted,
            frontAspect = vm.frontAspect,
            backAspect = vm.backAspect,
            quality = vm.quality,
            lifecycleOwner = lifecycleOwner,
            backView = backView,
            frontView = frontView,
            composedView = composedView,
            pip = pip,
            saveAspect = vm.saveAspect
        )
        renderedMode = controller.activeMode
        boundFor = vm.mode
        fallbackFrom = if (controller.activeMode != vm.mode) vm.mode else null
        ready = true
    }


    // Stillbild av det som syns: sammansatt bild i Dual View, annars en per kamera.
    fun takePhoto() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val shots = when {
            !controller.supported -> listOf("camera" to backView)
            displayMode == RecordingMode.DUAL_VIEW -> listOf("combined" to composedView)
            else -> listOfNotNull(
                if (!hideBack) "back" to backView else null,
                if (!hideFront) "front" to frontView else null
            )
        }
        val bitmaps = shots.mapNotNull { (key, view) -> view?.bitmap?.let { key to it } }
        if (bitmaps.isEmpty()) {
            status = "No picture to save yet"
            return
        }
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                bitmaps.count { (key, bmp) -> PhotoSaver.save(context, bmp, "dualshot_photo_${key}_$stamp") }
            }
            status = when (saved) {
                0 -> "Could not save the photo"
                1 -> "Photo saved – Gallery → Photos"
                else -> "$saved photos saved – Gallery → Photos"
            }
        }
    }

    // Timer: nollställs vid start och står still när man pausar.
    LaunchedEffect(isRecording) {
        if (isRecording) elapsed = 0
    }
    LaunchedEffect(isRecording, paused) {
        while (isRecording && !paused) {
            delay(1000)
            elapsed++
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Toppfält
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconChip(DuelIconKind.CLOSE) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (isRecording) controller.stopRecording()
                onExit()
            }
            Spacer(Modifier.width(12.dp))
            TimerPill(elapsed, isRecording && !paused)
            if (isRecording) {
                Spacer(Modifier.width(8.dp))
                IconChip(if (paused) DuelIconKind.PLAY else DuelIconKind.PAUSE, active = paused) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (paused) {
                        if (controller.resumeRecording()) {
                            paused = false
                            status = "Recording resumed – same file"
                        }
                    } else if (controller.pauseRecording()) {
                        paused = true
                        status = "Paused – resume to continue the same file"
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            IconChip(DuelIconKind.GRID, active = showGrid) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showGrid = !showGrid
            }
            Spacer(Modifier.width(8.dp))
            IconChip(DuelIconKind.SETTINGS, active = showTune) { showTune = !showTune }
        }

        // Förhandsvisning
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
            if (!controller.supported) {
                // Enskild kamera: en fullmatad förhandsvisning i valt format.
                FramedFeed(
                    backView, vm.backAspect, label = "CAMERA", modifier = Modifier.fillMaxSize(),
                    zoom = backZoom,
                    onZoom = { f ->
                        val (bz, _) = controller.setZoom(back = backZoom * f)
                        backZoom = bz
                    },
                    onFocus = { nx, ny -> controller.focusAt(isFront = false, nx = nx, ny = ny, view = backView) },
                    onRelease = { if (backView === it) backView = null }
                ) { backView = it }
            } else {
                if (displayMode == RecordingMode.DUAL_VIEW) {
                    val swap = pipLive.swapCameras
                    val largeAspect = if (swap) vm.frontAspect else vm.backAspect
                    val smallAspect = if (swap) vm.backAspect else vm.frontAspect
                    // Med eget filformat visar ramen hela filen (t.ex. 16:9 för YouTube).
                    val frameAspect = vm.saveAspect ?: largeAspect
                    Box(
                        modifier = Modifier.fillMaxSize().padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Videon och den lilla kamerans ram delar exakt samma yta,
                        // så ramen hamnar där den faktiskt spelas in.
                        Box(Modifier.aspectRatio(frameAspect.ratio())) {
                            Feed(
                                composedView,
                                label = if (swap) "FRONT" else "BACK",
                                ratio = vm.saveAspect?.let { "FILE " + it.label.substringBefore(' ') } ?: largeAspect.label,
                                modifier = Modifier.fillMaxSize(),
                                zoom = if (swap) frontZoom else backZoom,
                                onZoom = { f ->
                                    if (swap) {
                                        val (_, fz) = controller.setZoom(front = frontZoom * f)
                                        frontZoom = fz
                                    } else {
                                        val (bz, _) = controller.setZoom(back = backZoom * f)
                                        backZoom = bz
                                    }
                                },
                                onFocus = { nx, ny ->
                                    controller.focusAt(isFront = swap, nx = nx, ny = ny, view = composedView)
                                },
                                onRelease = { if (composedView === it) composedView = null }
                            ) { composedView = it }

                            // Pausad stor kamera: svart, bara lilla kameran syns.
                            if (mainPaused) PausedLarge(
                                hole = if (pipLive.visible && !pipLive.paused) pipLive else null,
                                smallAspect = smallAspect
                            )

                            if (pipLive.visible) PipOutline(
                                layout = pipLive,
                                aspect = smallAspect,
                                onDrag = { dx, dy ->
                                    pipLive = pipLive.copy(
                                        cx = (pipLive.cx + dx).coerceIn(0.12f, 0.88f),
                                        cy = (pipLive.cy + dy).coerceIn(0.12f, 0.88f)
                                    )
                                },
                                onResize = { size -> pipLive = pipLive.copy(scale = size) },
                                onCommit = {
                                    pip = pipLive
                                    status = if (isRecording) "Small camera moved – applies to the next part"
                                    else "Small camera position saved"
                                },
                                onTogglePause = {
                                    pipLive = pipLive.copy(paused = !pipLive.paused)
                                    pip = pipLive
                                    status = when {
                                        !isRecording -> if (pipLive.paused) "Small camera paused" else "Small camera running"
                                        else -> if (pipLive.paused) "Small camera paused – applies to the next take"
                                        else "Small camera running – applies to the next take"
                                    }
                                },
                                onClose = {
                                    pipLive = pipLive.copy(visible = false, paused = false)
                                    pip = pipLive
                                    if (isRecording) status = "Small camera off – next clip part"
                                }
                            )

                            // Pausa/återuppta den stora kameran.
                            Box(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 40.dp)) {
                                PipChip(if (mainPaused) DuelIconKind.PLAY else DuelIconKind.PAUSE) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    mainPaused = !mainPaused
                                    status = when {
                                        !isRecording -> if (mainPaused) "Large camera paused" else "Large camera running"
                                        else -> if (mainPaused) "Large camera paused – applies to the next take"
                                        else "Large camera running – applies to the next take"
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Samma två vyer oavsett layout – delningen går att dra med fingret
                    // och varje ruta kan pausas eller klickas bort under inspelning.
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val totalPx = (if (stacked) constraints.maxHeight else constraints.maxWidth).toFloat()
                        val showBack = !hideBack
                        val showFront = !hideFront
                        val aFrac = when {
                            showBack && showFront -> split
                            showBack -> 1f
                            else -> 0f
                        }
                        val cellA = if (stacked) Modifier.fillMaxWidth().fillMaxHeight(aFrac).align(Alignment.TopCenter)
                        else Modifier.fillMaxHeight().fillMaxWidth(aFrac).align(Alignment.CenterStart)
                        val cellB = if (stacked) Modifier.fillMaxWidth().fillMaxHeight(1f - aFrac).align(Alignment.BottomCenter)
                        else Modifier.fillMaxHeight().fillMaxWidth(1f - aFrac).align(Alignment.CenterEnd)

                        if (showBack) Box(cellA.padding(3.dp)) {
                            FramedFeed(
                                backView, vm.backAspect, label = "BACK", modifier = Modifier.fillMaxSize(),
                                zoom = backZoom,
                                onZoom = { f ->
                                    val (bz, _) = controller.setZoom(back = backZoom * f)
                                    backZoom = bz
                                },
                                onRelease = { if (backView === it) backView = null }
                            ) { backView = it }
                            if (backPaused) PausedTile()
                            if (isRecording) TileChips(
                                paused = backPaused,
                                canHide = showFront,
                                onPause = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (backPaused) {
                                        if (controller.resumeCamera(back = true)) {
                                            backPaused = false
                                            status = "BACK resumed – same file"
                                        }
                                    } else if (controller.pauseCamera(back = true)) {
                                        backPaused = true
                                        status = "BACK paused – FRONT keeps recording"
                                    } else {
                                        status = "BACK could not pause on its own – use the top pause"
                                    }
                                },
                                onHide = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    hideBack = true
                                    backPaused = false
                                    controller.stopCamera(back = true)
                                    status = if (isRecording) "BACK removed – FRONT keeps recording"
                                    else "BACK hidden – FRONT only"
                                }
                            )
                        }
                        if (showFront) Box(cellB.padding(3.dp)) {
                            FramedFeed(
                                frontView, vm.frontAspect, label = "FRONT", modifier = Modifier.fillMaxSize(),
                                zoom = frontZoom,
                                onZoom = { f ->
                                    val (_, fz) = controller.setZoom(front = frontZoom * f)
                                    frontZoom = fz
                                },
                                onRelease = { if (frontView === it) frontView = null }
                            ) { frontView = it }
                            if (frontPaused) PausedTile()
                            if (isRecording) TileChips(
                                paused = frontPaused,
                                canHide = showBack,
                                onPause = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (frontPaused) {
                                        if (controller.resumeCamera(back = false)) {
                                            frontPaused = false
                                            status = "FRONT resumed – same file"
                                        }
                                    } else if (controller.pauseCamera(back = false)) {
                                        frontPaused = true
                                        status = "FRONT paused – BACK keeps recording"
                                    } else {
                                        status = "FRONT could not pause on its own – use the top pause"
                                    }
                                },
                                onHide = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    hideFront = true
                                    frontPaused = false
                                    controller.stopCamera(back = false)
                                    status = if (isRecording) "FRONT removed – BACK keeps recording"
                                    else "FRONT hidden – BACK only"
                                }
                            )
                        }
                        if (showBack && showFront) SplitHandle(
                            stacked = stacked,
                            modifier = if (stacked) Modifier.align(Alignment.Center).offset {
                                IntOffset(0, (aFrac * totalPx - 12.dp.toPx()).roundToInt())
                            } else Modifier.align(Alignment.CenterStart).offset {
                                IntOffset((aFrac * totalPx - 12.dp.toPx()).roundToInt(), 0)
                            },
                            onDrag = { delta -> split = (aFrac + delta / totalPx).coerceIn(0.3f, 0.7f) }
                        )
                    }
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
            if (showGrid) GridOverlay(Modifier.fillMaxSize().padding(11.dp))
        }

        // Ändra live: läge, kvalitet, format och layout.
        if (showTune) {
            val dual = controller.supported
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isRecording) {
                    Text("One take = one file · changes apply to the next take", color = DuelOrangeLight, fontSize = 11.sp)
                }
                if (dual) TuneRow("MODE") {
                    SegmentedControl(
                        options = listOf("Dual", "Separate", "Both"),
                        selectedIndex = vm.mode.ordinal,
                        onSelect = { vm.mode = RecordingMode.values()[it] }
                    )
                }
                if (fallbackFrom == RecordingMode.DUAL_VIEW) {
                    Text(
                        "This phone can't compose the two cameras into one file, so Dual and Both save separate BACK and FRONT files – exactly like Separate.",
                        color = DuelOrangeLight, fontSize = 11.sp
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
                    if (displayMode == RecordingMode.DUAL_VIEW) TuneRow("FILE") {
                        val formats = listOf(null, AspectOption.R_9_16, AspectOption.R_16_9, AspectOption.R_1_1)
                        SegmentedControl(
                            options = listOf("Auto", "9:16", "16:9", "1:1"),
                            selectedIndex = formats.indexOf(vm.saveAspect).coerceAtLeast(0),
                            onSelect = { vm.saveAspect = formats[it] }
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

        // Kontrollrad: zoom, slutare och småbildsverktyg – synliga i alla lägen.
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
            PhotoButton(enabled = ready && controller.canBind, onClick = { takePhoto() })
            Spacer(Modifier.width(28.dp))
            RecordButton(
                recording = isRecording,
                enabled = ready && controller.canBind,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (!isRecording) {
                        val freeMb = runCatching { context.filesDir.usableSpace / (1024 * 1024) }.getOrDefault(0L)
                        if (freeMb in 1..299) {
                            status = "Low storage: ${freeMb} MB free – free up space first"
                        } else {
                            controller.startRecording(
                                onStarted = { names ->
                                    savedNames = names
                                    isRecording = true
                                    paused = false
                                    backPaused = false
                                    frontPaused = false
                                    hideBack = false
                                    hideFront = false
                                    status = "Recording – one continuous file"
                                },
                                onError = { status = it }
                            )
                        }
                    } else {
                        controller.stopRecording()
                        isRecording = false
                        paused = false
                        backPaused = false
                        frontPaused = false
                        hideBack = false
                        hideFront = false
                        status = "Stopped. Saved to Gallery → Movies/DualShots"
                    }
                }
            )
            Spacer(Modifier.width(28.dp))
            Spacer(Modifier.size(52.dp))
            }

            if (controller.supported) {
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ZoomChip(
                        zoom = if (pipLive.swapCameras) frontZoom else backZoom,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val current = if (pipLive.swapCameras) frontZoom else backZoom
                            val next = when {
                                current < 1.5f -> 2f
                                current < 2.5f -> 3f
                                else -> 1f
                            }
                            if (pipLive.swapCameras) {
                                val (_, fz) = controller.setZoom(front = next)
                                frontZoom = fz
                            } else {
                                val (bz, _) = controller.setZoom(back = next)
                                backZoom = bz
                            }
                            status = "Zoom " + "%.1f".format(next) + "× on the large camera"
                        }
                    )

                    if (displayMode == RecordingMode.DUAL_VIEW) {
                        SwapButton(
                            swapped = pipLive.swapCameras,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                pipLive = pipLive.copy(swapCameras = !pipLive.swapCameras)
                                pip = pipLive
                                status = if (isRecording) "Camera swap – applies to the next take"
                                else if (pipLive.swapCameras) "Front camera large, back camera small"
                                else "Back camera large, front camera small"
                            }
                        )
                    }

                    PipToggleButton(
                        off = !pipLive.visible,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (displayMode != RecordingMode.DUAL_VIEW && fallbackFrom == RecordingMode.DUAL_VIEW) {
                                status = "Dual View isn't supported on this phone – use Separate or Both"
                            } else if (displayMode != RecordingMode.DUAL_VIEW) {
                                // I Separate/Both finns ingen liten kamera – byt till Dual View.
                                vm.mode = RecordingMode.DUAL_VIEW
                                pipLive = pipLive.copy(visible = true, paused = false)
                                pip = pipLive
                                status = if (isRecording) "Dual View – applies to the next take"
                                else "Dual View – drag, resize, pause or close the small camera"
                            } else {
                                pipLive = pipLive.copy(visible = !pipLive.visible, paused = false)
                                pip = pipLive
                                status = if (isRecording) {
                                    if (pipLive.visible) "Small camera on – applies to the next take"
                                    else "Small camera off – applies to the next take"
                                } else {
                                    if (pipLive.visible) "Small camera on"
                                    else "Small camera off – main camera only"
                                }
                            }
                        }
                    )
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

/** Klassiska 3×3-hjälplinjer över förhandsvisningen. */
@Composable
private fun GridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val line = Color.White.copy(alpha = .22f)
        val width = 1.dp.toPx()
        for (i in 1..2) {
            val x = size.width * i / 3f
            drawLine(line, Offset(x, 0f), Offset(x, size.height), width)
            val y = size.height * i / 3f
            drawLine(line, Offset(0f, y), Offset(size.width, y), width)
        }
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
    zoom: Float = 1f,
    onZoom: ((Float) -> Unit)? = null,
    onFocus: ((Float, Float) -> Unit)? = null,
    onRelease: (PreviewView) -> Unit,
    onView: (PreviewView) -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Feed(
            view, label = label, ratio = aspect.label,
            modifier = Modifier.aspectRatio(aspect.ratio()),
            zoom = zoom,
            onZoom = onZoom,
            onFocus = onFocus,
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
    zoom: Float = 1f,
    onZoom: ((Float) -> Unit)? = null,
    onFocus: ((Float, Float) -> Unit)? = null,
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
        if (onZoom != null || onFocus != null) {
            // Genomskinligt lager: två fingrar zoomar, ett tryck fokuserar.
            var focusPoint by remember { mutableStateOf<Offset?>(null) }
            val haptic = LocalHapticFeedback.current
            Box(
                modifier = Modifier.fillMaxSize().pointerInput(onZoom, onFocus) {
                    coroutineScope {
                        launch {
                            detectTransformGestures { _, _, factor, _ ->
                                if (factor != 1f) onZoom?.invoke(factor)
                            }
                        }
                        launch {
                            detectTapGestures { pos ->
                                focusPoint = pos
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onFocus?.invoke(pos.x / size.width, pos.y / size.height)
                            }
                        }
                    }
                }
            )
            val ring = focusPoint
            if (ring != null) {
                LaunchedEffect(ring) {
                    delay(1100)
                    focusPoint = null
                }
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        color = DuelGold,
                        radius = 24.dp.toPx(),
                        center = ring,
                        style = Stroke(width = 1.6.dp.toPx())
                    )
                }
            }
        }
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
        if (zoom > 1.01f) {
            Text(
                "${(zoom * 10).roundToInt() / 10f}×", color = DuelText,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = .45f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
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
/** Ram för den lilla kameran i Dual View – bilden ritas av sammansättningen. */
@Composable
private fun PipOutline(
    layout: PipLayout,
    aspect: AspectOption,
    onDrag: (Float, Float) -> Unit,
    onResize: (Float) -> Unit,
    onCommit: () -> Unit,
    onTogglePause: () -> Unit,
    onClose: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val scale = layout.scale.coerceIn(0.18f, 0.6f)
        val liveScale = rememberUpdatedState(scale)
        val pipW = w * scale
        val pipH = pipW / aspect.ratio()
        val left = (layout.cx * w - pipW / 2f).coerceIn(0f, (w - pipW).coerceAtLeast(0f))
        val top = (layout.cy * h - pipH / 2f).coerceIn(0f, (h - pipH).coerceAtLeast(0f))

        Box(
            modifier = Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(with(density) { pipW.toDp() }, with(density) { pipH.toDp() })
                .clip(RoundedCornerShape(12.dp))
                .border(1.5.dp, Color.White.copy(alpha = if (layout.paused) .28f else .6f), RoundedCornerShape(12.dp))
                .pointerInput(aspect) {
                    detectDragGestures(
                        onDragEnd = { onCommit() },
                        onDragCancel = { onCommit() }
                    ) { change, drag ->
                        change.consume()
                        onDrag(drag.x / w, drag.y / h)
                    }
                }
        ) {
            if (layout.paused) Text(
                "PAUSED", color = Color.White.copy(alpha = .82f),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.sp,
                modifier = Modifier.align(Alignment.Center)
            )
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PipChip(if (layout.paused) DuelIconKind.PLAY else DuelIconKind.PAUSE, onClick = onTogglePause)
                PipChip(DuelIconKind.CLOSE, onClick = onClose)
            }
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).size(26.dp)
                    .clip(RoundedCornerShape(topStart = 8.dp))
                    .background(Color.Black.copy(alpha = .35f))
                    .pointerInput(aspect) {
                        detectDragGestures(
                            onDragEnd = { onCommit() },
                            onDragCancel = { onCommit() }
                        ) { change, drag ->
                            change.consume()
                            onResize((liveScale.value + drag.x / w).coerceIn(0.18f, 0.6f))
                        }
                    }
            )
        }
    }
}

/** Svart ruta när en kamera är pausad – ingen bild syns igenom. */
@Composable
private fun PausedTile() {
    Box(
        Modifier.fillMaxSize().padding(3.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "PAUSED", color = Color.White.copy(alpha = .82f),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.sp
        )
    }
}

/** Stora kameran pausad i Dual View: svart överallt utom den lilla kamerans ruta. */
@Composable
private fun PausedLarge(hole: PipLayout?, smallAspect: AspectOption) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (hole == null) {
                drawRect(Color.Black)
            } else {
                val scale = hole.scale.coerceIn(0.18f, 0.6f)
                val pw = w * scale
                val ph = pw / smallAspect.ratio()
                val left = (hole.cx * w - pw / 2f).coerceIn(0f, (w - pw).coerceAtLeast(0f))
                val top = (hole.cy * h - ph / 2f).coerceIn(0f, (h - ph).coerceAtLeast(0f))
                drawRect(Color.Black, Offset(0f, 0f), androidx.compose.ui.geometry.Size(w, top))
                drawRect(Color.Black, Offset(0f, top + ph), androidx.compose.ui.geometry.Size(w, h - top - ph))
                drawRect(Color.Black, Offset(0f, top), androidx.compose.ui.geometry.Size(left, ph))
                drawRect(Color.Black, Offset(left + pw, top), androidx.compose.ui.geometry.Size(w - left - pw, ph))
            }
        }
        Text(
            "PAUSED", color = Color.White.copy(alpha = .7f),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
        )
    }
}

/** Tar en stillbild – fungerar både med och utan pågående inspelning. */
@Composable
private fun PhotoButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(52.dp).clip(CircleShape)
            .background(DuelSurface2).border(1.dp, DuelLine, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        DuelIcon(DuelIconKind.CAMERA, if (enabled) DuelText else DuelTextMuted, 22.dp)
    }
}

/** Pausa-/kryssknappar uppe i hörnet på en inspelningsruta. */
@Composable
private fun TileChips(
    paused: Boolean,
    canHide: Boolean,
    onPause: () -> Unit,
    onHide: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PipChip(if (paused) DuelIconKind.PLAY else DuelIconKind.PAUSE, onClick = onPause)
            if (canHide) PipChip(DuelIconKind.CLOSE, onClick = onHide)
        }
    }
}

/** Handtag mellan de två rutorna: dra för att göra ena bilden större. */
@Composable
private fun SplitHandle(stacked: Boolean, modifier: Modifier, onDrag: (Float) -> Unit) {
    Box(
        modifier = modifier
            .then(if (stacked) Modifier.fillMaxWidth().height(24.dp) else Modifier.fillMaxHeight().width(24.dp))
            .pointerInput(stacked) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onDrag(if (stacked) drag.y else drag.x)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .then(if (stacked) Modifier.size(44.dp, 6.dp) else Modifier.size(6.dp, 44.dp))
                .clip(CircleShape)
                .background(Color.White.copy(alpha = .55f))
        )
    }
}

/** Liten, flyttbar kamera ovanpå den stora bilden i Dual View. */
@Composable
private fun PipInset(
    view: PreviewView?,
    aspect: AspectOption,
    label: String,
    layout: PipLayout,
    zoom: Float,
    paused: Boolean,
    onTogglePause: () -> Unit,
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onZoom: (Float) -> Unit,
    onResize: (Float) -> Unit,
    onCommit: () -> Unit,
    onRelease: (PreviewView) -> Unit,
    onView: (PreviewView) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val scale = layout.scale.coerceIn(0.18f, 0.6f)
        val pipW = w * scale
        val pipH = pipW / aspect.ratio()
        val left = (layout.cx * w - pipW / 2f).coerceIn(0f, (w - pipW).coerceAtLeast(0f))
        val top = (layout.cy * h - pipH / 2f).coerceIn(0f, (h - pipH).coerceAtLeast(0f))
        val liveScale = rememberUpdatedState(scale)

        Box(
            modifier = Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(with(density) { pipW.toDp() }, with(density) { pipH.toDp() })
        ) {
            // Ett finger flyttar rutan, två fingrar zoomar kameran.
            Box(
                modifier = Modifier.fillMaxSize().pointerInput(aspect) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var pinching = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val factor = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (factor != 1f) {
                                pinching = true
                                onZoom(factor)
                            } else if (!pinching && pan != Offset.Zero) {
                                onDrag(pan.x / w, pan.y / h)
                            }
                            event.changes.forEach { it.consume() }
                            if (event.changes.none { it.pressed }) break
                        }
                        onCommit()
                    }
                }
            ) {
                FramedFeed(
                    view = view,
                    aspect = aspect,
                    label = label,
                    modifier = Modifier.fillMaxSize(),
                    onRelease = onRelease,
                    onView = onView
                )
                if (paused) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .62f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "PAUSED", color = Color.White.copy(alpha = .82f),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 9.sp, letterSpacing = 1.sp
                        )
                    }
                }
                if (zoom > 1.01f) {
                    Text(
                        "${(zoom * 10).roundToInt() / 10f}×", color = DuelText,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 9.sp,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 34.dp)
                            .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = .45f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            // Pausa eller stäng av lilla kameran.
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PipChip(if (paused) DuelIconKind.PLAY else DuelIconKind.PAUSE, onClick = onTogglePause)
                PipChip(DuelIconKind.CLOSE, onClick = onClose)
            }

            // Hörnhandtag: dra utåt för större ruta, inåt för mindre.
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).size(30.dp)
                    .clip(RoundedCornerShape(topStart = 10.dp))
                    .background(Color.Black.copy(alpha = .4f))
                    .pointerInput(aspect) {
                        var start = liveScale.value
                        detectDragGestures(
                            onDragStart = { start = liveScale.value },
                            onDragEnd = { onCommit() },
                            onDragCancel = { onCommit() }
                        ) { change, drag ->
                            change.consume()
                            onResize((start + drag.x / w).coerceIn(0.18f, 0.6f))
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                    val stroke = 1.6.dp.toPx()
                    drawLine(
                        color = Color.White.copy(alpha = .85f),
                        start = Offset(size.width, size.height * 0.42f),
                        end = Offset(size.width * 0.42f, size.height),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color.White.copy(alpha = .55f),
                        start = Offset(size.width, size.height * 0.78f),
                        end = Offset(size.width * 0.78f, size.height),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

/** Cyklar zoom på den stora kameran (1× → 2× → 3× → 1×). */
@Composable
private fun ZoomChip(zoom: Float, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DuelSurface2)
            .border(1.dp, if (zoom > 1.01f) DuelOrange else DuelLine, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            (kotlin.math.round(zoom * 10f) / 10f).toString().removeSuffix(".0") + "×",
            color = if (zoom > 1.01f) DuelOrangeLight else DuelText,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun PipChip(kind: DuelIconKind, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(30.dp).clip(CircleShape)
            .background(Color.Black.copy(alpha = .62f))
            .border(1.dp, Color.White.copy(alpha = .34f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        DuelIcon(kind, Color.White, 15.dp)
    }
}

/** Slår på/av den lilla kameran i Dual View. */
@Composable
private fun PipToggleButton(off: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DuelSurface2)
            .border(1.dp, if (off) DuelLine else DuelOrange, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DuelIcon(DuelIconKind.PIP, if (off) DuelTextMuted else DuelOrangeLight, 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            if (off) "OFF" else "SMALL",
            color = DuelText, fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 10.sp, letterSpacing = 1.sp
        )
    }
}

/** Byter vilken kamera som är stor respektive liten i Dual View. */
@Composable
private fun SwapButton(swapped: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DuelSurface2)
            .border(1.dp, if (swapped) DuelOrange else DuelLine, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DuelIcon(DuelIconKind.SWITCH, DuelOrangeLight, 18.dp, Modifier.rotate(if (swapped) 180f else 0f))
        Spacer(Modifier.width(8.dp))
        Text(
            "SWAP",
            color = DuelText, fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 10.sp, letterSpacing = 1.sp
        )
    }
}
