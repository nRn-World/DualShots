package com.nrworld.duelshot.ui.record

import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nrworld.duelshot.camera.DualCameraController
import com.nrworld.duelshot.camera.DualGlComposer
import com.nrworld.duelshot.camera.PipLayout
import com.nrworld.duelshot.model.AspectOption
import com.nrworld.duelshot.model.QualityOption
import com.nrworld.duelshot.model.RecordingMode
import com.nrworld.duelshot.ui.AppViewModel
import com.nrworld.duelshot.ui.components.DuelIcon
import com.nrworld.duelshot.ui.components.DuelIconKind
import com.nrworld.duelshot.ui.components.PremiumDialog
import com.nrworld.duelshot.ui.theme.DuelAccent
import com.nrworld.duelshot.ui.theme.DuelBg2
import com.nrworld.duelshot.ui.theme.DuelGold
import com.nrworld.duelshot.ui.theme.DuelGradient
import com.nrworld.duelshot.ui.theme.DuelLine
import com.nrworld.duelshot.ui.theme.DuelLineStrong
import com.nrworld.duelshot.ui.theme.DuelOrange
import com.nrworld.duelshot.ui.theme.DuelOrangeLight
import com.nrworld.duelshot.ui.theme.DuelRecord
import com.nrworld.duelshot.ui.theme.DuelSurface
import com.nrworld.duelshot.ui.theme.DuelSurface2
import com.nrworld.duelshot.ui.theme.DuelText
import com.nrworld.duelshot.ui.theme.DuelTextFaint
import com.nrworld.duelshot.ui.theme.DuelTextMuted
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val PIP_MIN = 0.16f
private const val PIP_MAX = 0.60f
private const val PIP_DEFAULT_CX = 0.74f
private const val PIP_DEFAULT_CY = 0.24f
private const val PIP_DEFAULT_SCALE = 0.34f
private val Ink = Color(0xFF04060D)

private data class PipBoxRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val normCx: Float,
    val normCy: Float,
    val normScaleX: Float,
    val normScaleY: Float
)

private fun computePipRect(
    stageW: Float,
    stageH: Float,
    aspect: AspectOption,
    layout: PipLayout
): PipBoxRect {
    val safeW = stageW.coerceAtLeast(1f)
    val safeH = stageH.coerceAtLeast(1f)
    val ratio = aspect.ratio()
    var pw = safeW * layout.scale.coerceIn(PIP_MIN, PIP_MAX)
    var ph = pw / ratio

    val maxH = safeH * 0.72f
    val maxW = safeW * 0.56f
    if (ph > maxH) {
        ph = maxH
        pw = ph * ratio
    }
    if (pw > maxW) {
        pw = maxW
        ph = pw / ratio
    }

    val mx = safeW * 0.035f
    val my = safeH * 0.035f
    val left = (layout.cx * safeW - pw / 2f).coerceIn(mx, (safeW - pw - mx).coerceAtLeast(mx))
    val top = (layout.cy * safeH - ph / 2f).coerceIn(my, (safeH - ph - my).coerceAtLeast(my))

    return PipBoxRect(
        left = left,
        top = top,
        width = pw,
        height = ph,
        normCx = ((left + pw / 2f) / safeW).coerceIn(0.05f, 0.95f),
        normCy = ((top + ph / 2f) / safeH).coerceIn(0.05f, 0.95f),
        normScaleX = (pw / safeW).coerceIn(PIP_MIN, PIP_MAX),
        normScaleY = (ph / safeH).coerceIn(0.12f, 0.78f)
    )
}

@Composable
fun RecordScreen(vm: AppViewModel, onExit: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val localView = LocalView.current
    val controller = remember { DualCameraController(context) }

    var singleView by remember { mutableStateOf<PreviewView?>(null) }
    var backView by remember { mutableStateOf<PreviewView?>(null) }
    var frontView by remember { mutableStateOf<PreviewView?>(null) }
    var composedView by remember { mutableStateOf<PreviewView?>(null) }

    var status by remember { mutableStateOf("Starting cameras…") }
    var camReady by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var singleFlipTick by remember { mutableIntStateOf(0) }

    var isRecording by remember { mutableStateOf(false) }
    var takeMode by remember { mutableStateOf<RecordingMode?>(null) }
    var takeFrontAspect by remember { mutableStateOf<AspectOption?>(null) }
    var takeBackAspect by remember { mutableStateOf<AspectOption?>(null) }
    var takeQuality by remember { mutableStateOf<QualityOption?>(null) }

    var elapsed by remember { mutableIntStateOf(0) }
    var savedNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingClipNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var showSetupSheet by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var premiumFeature by remember { mutableStateOf<String?>(null) }

    // Toast-meddelande ovanför bottendockan (samma som webb-previewn).
    var toastText by remember { mutableStateOf<String?>(null) }
    var toastTick by remember { mutableIntStateOf(0) }
    fun showToast(msg: String) {
        toastText = msg
        toastTick++
    }
    LaunchedEffect(toastTick) {
        if (toastText != null) {
            delay(2500)
            toastText = null
        }
    }

    // Bild-i-bild-tips som tonas bort automatiskt.
    var showPipHint by remember { mutableStateOf(true) }
    LaunchedEffect(vm.mode) {
        if (vm.mode == RecordingMode.DUAL_VIEW) {
            showPipHint = true
            delay(5500)
            showPipHint = false
        }
    }

    val initialPip = remember {
        PipLayout(
            cx = vm.pipCx,
            cy = vm.pipCy,
            scale = vm.pipScale,
            scaleY = vm.pipScale,
            swapCameras = vm.swapCameras,
            visible = true,
            paused = false
        )
    }
    var pip by remember { mutableStateOf(initialPip) }
    var pipLive by remember { mutableStateOf(initialPip) }

    var backZoom by remember { mutableFloatStateOf(1f) }
    var frontZoom by remember { mutableFloatStateOf(1f) }
    var backHwZoom by remember { mutableFloatStateOf(1f) }
    var frontHwZoom by remember { mutableFloatStateOf(1f) }

    // Staplad vy i Separate / Both (front överst eller bak överst, precis som webb-previewn).
    var frontFirst by remember { mutableStateOf(true) }
    var split by remember { mutableFloatStateOf(0.5f) }
    var hideBack by remember { mutableStateOf(false) }
    var hideFront by remember { mutableStateOf(false) }
    var backPaused by remember { mutableStateOf(false) }
    var frontPaused by remember { mutableStateOf(false) }

    val effectiveMode = takeMode ?: vm.mode
    val effectiveFrontAspect = takeFrontAspect ?: vm.frontAspect
    val effectiveBackAspect = takeBackAspect ?: vm.backAspect
    val effectiveQuality = takeQuality ?: vm.quality
    val isSingleCamera = camReady && (!controller.supported || controller.singleFallback)

    BackHandler {
        if (showSetupSheet) {
            showSetupSheet = false
        } else {
            if (isRecording) controller.stopRecording()
            onExit()
        }
    }

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    DisposableEffect(isRecording) {
        localView.keepScreenOn = isRecording
        onDispose { localView.keepScreenOn = false }
    }

    LaunchedEffect(Unit) {
        status = controller.init()
        camReady = controller.canBind
    }

    // I Dual View hanterar DualGlComposer PiP i realtid i OpenGL utan kamera-ombindning.
    val bindPipTrigger = if (effectiveMode == RecordingMode.BOTH) pip else null

    LaunchedEffect(
        camReady,
        singleView,
        backView,
        frontView,
        composedView,
        vm.mode,
        vm.frontAspect,
        vm.backAspect,
        vm.quality,
        bindPipTrigger,
        singleFlipTick,
        isRecording
    ) {
        if (!camReady) return@LaunchedEffect
        if (isRecording) return@LaunchedEffect

        if (!controller.supported) {
            if (singleView == null) return@LaunchedEffect
            status = controller.bind(
                wanted = RecordingMode.SEPARATE,
                frontAspect = vm.frontAspect,
                backAspect = vm.backAspect,
                quality = vm.quality,
                lifecycleOwner = lifecycleOwner,
                backView = singleView,
                frontView = singleView,
                composedView = null,
                pip = pip
            )
            ready = true
            return@LaunchedEffect
        }

        when (vm.mode) {
            RecordingMode.DUAL_VIEW -> {
                status = controller.bind(
                    wanted = RecordingMode.DUAL_VIEW,
                    frontAspect = vm.frontAspect,
                    backAspect = vm.backAspect,
                    quality = vm.quality,
                    lifecycleOwner = lifecycleOwner,
                    backView = singleView,
                    frontView = singleView,
                    composedView = null,
                    pip = pipLive
                )
                val (bz, fz) = controller.setZoom(back = backZoom, front = frontZoom)
                backHwZoom = bz
                frontHwZoom = fz
                ready = true
            }

            RecordingMode.SEPARATE -> {
                if (backView == null || frontView == null) return@LaunchedEffect
                status = controller.bind(
                    wanted = RecordingMode.SEPARATE,
                    frontAspect = vm.frontAspect,
                    backAspect = vm.backAspect,
                    quality = vm.quality,
                    lifecycleOwner = lifecycleOwner,
                    backView = backView,
                    frontView = frontView,
                    composedView = null,
                    pip = pip
                )
                ready = true
            }

            RecordingMode.BOTH -> {
                if (backView == null || frontView == null || composedView == null) return@LaunchedEffect
                status = controller.bind(
                    wanted = RecordingMode.BOTH,
                    frontAspect = vm.frontAspect,
                    backAspect = vm.backAspect,
                    quality = vm.quality,
                    lifecycleOwner = lifecycleOwner,
                    backView = backView,
                    frontView = frontView,
                    composedView = composedView,
                    pip = pip
                )
                ready = true
            }
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) elapsed = 0
    }
    LaunchedEffect(isRecording, paused) {
        while (isRecording && !paused) {
            delay(1000)
            elapsed++
        }
    }

    fun persistPip(next: PipLayout, commitToCamera: Boolean) {
        pipLive = next
        vm.pipCx = next.cx
        vm.pipCy = next.cy
        vm.pipScale = next.scale
        vm.swapCameras = next.swapCameras
        if (commitToCamera) {
            pip = next
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Toppfält (.rec-top)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconChip(DuelIconKind.CLOSE) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isRecording) controller.stopRecording()
                    onExit()
                }
                Spacer(Modifier.width(8.dp))
                TimerPill(seconds = elapsed, recording = isRecording, paused = paused)
                if (isRecording) {
                    Spacer(Modifier.width(8.dp))
                    IconChip(
                        kind = if (paused) DuelIconKind.PLAY else DuelIconKind.PAUSE,
                        active = paused
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (paused) {
                            if (controller.resumeRecording()) {
                                paused = false
                                status = "Recording · ${effectiveMode.title.lowercase()}"
                                showToast("Recording")
                            }
                        } else if (controller.pauseRecording()) {
                            paused = true
                            status = "Paused — resume to continue the same file"
                            showToast("Paused")
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                IconChip(DuelIconKind.GRID, active = showGrid) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showGrid = !showGrid
                    showToast(if (showGrid) "Grid on" else "Grid off")
                }
                Spacer(Modifier.width(8.dp))
                IconChip(DuelIconKind.SETTINGS, active = showSetupSheet) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showSetupSheet = true
                }
            }

            // Fullhöjds kamerakort (.rec-preview)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DuelSurface)
                    .border(
                        width = 1.dp,
                        color = if (isRecording) DuelRecord.copy(alpha = .55f) else DuelLine,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(6.dp)
            ) {
                if (!camReady) {
                    CameraPlaceholder("Starting cameras")
                } else if (isSingleCamera) {
                    val useFront = controller.singleUseFront
                    val aspect = if (useFront) effectiveFrontAspect else effectiveBackAspect
                    FeedTile(
                        view = singleView,
                        badge = "CAMERA · ${aspect.shortLabel()}",
                        zoom = if (useFront) frontZoom else backZoom,
                        onZoom = { factor ->
                            if (useFront) {
                                val (_, fz) = controller.setZoom(front = (frontZoom * factor).coerceIn(1f, 3f))
                                frontZoom = fz
                            } else {
                                val (bz, _) = controller.setZoom(back = (backZoom * factor).coerceIn(1f, 3f))
                                backZoom = bz
                            }
                        },
                        onFocus = { nx, ny ->
                            controller.focusAt(isFront = useFront, nx = nx, ny = ny, view = singleView)
                        },
                        onRelease = { if (singleView === it) singleView = null },
                        onView = { singleView = it },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (effectiveMode == RecordingMode.DUAL_VIEW) {
                    val swap = pipLive.swapCameras
                    val smallAspect = if (swap) effectiveBackAspect else effectiveFrontAspect
                    val backShaderZoom = (backZoom / backHwZoom.coerceAtLeast(1f)).coerceIn(1f, 3f)
                    val frontShaderZoom = (frontZoom / frontHwZoom.coerceAtLeast(1f)).coerceIn(1f, 3f)

                    DualGlViewStage(
                        controller = controller,
                        pipLive = pipLive,
                        smallAspect = smallAspect,
                        mainZoom = if (swap) frontZoom else backZoom,
                        smallZoom = if (swap) backZoom else frontZoom,
                        backShaderZoom = backShaderZoom,
                        frontShaderZoom = frontShaderZoom,
                        showPipHint = showPipHint,
                        onUpdateLive = { updated ->
                            showPipHint = false
                            pipLive = updated
                        },
                        onCommitLive = { committed ->
                            persistPip(committed, commitToCamera = false)
                        },
                        onMainZoom = { factor ->
                            if (swap) {
                                val desired = (frontZoom * factor).coerceIn(1f, 3f)
                                val (_, hwFz) = controller.setZoom(front = desired)
                                frontZoom = desired
                                frontHwZoom = hwFz
                            } else {
                                val desired = (backZoom * factor).coerceIn(1f, 3f)
                                val (hwBz, _) = controller.setZoom(back = desired)
                                backZoom = desired
                                backHwZoom = hwBz
                            }
                        },
                        onSmallZoom = { factor ->
                            showPipHint = false
                            if (swap) {
                                val desired = (backZoom * factor).coerceIn(1f, 3f)
                                val (hwBz, _) = controller.setZoom(back = desired)
                                backZoom = desired
                                backHwZoom = hwBz
                            } else {
                                val desired = (frontZoom * factor).coerceIn(1f, 3f)
                                val (_, hwFz) = controller.setZoom(front = desired)
                                frontZoom = desired
                                frontHwZoom = hwFz
                            }
                        },
                        onMainFocus = { nx, ny ->
                            controller.focusAt(isFront = swap, nx = nx, ny = ny, view = null)
                        },
                        onSmallFocus = { nx, ny ->
                            controller.focusAt(isFront = !swap, nx = nx, ny = ny, view = null)
                        },
                        onReset = { stageW, stageH ->
                            val defRaw = pipLive.copy(
                                cx = PIP_DEFAULT_CX,
                                cy = PIP_DEFAULT_CY,
                                scale = PIP_DEFAULT_SCALE
                            )
                            val clamped = computePipRect(stageW, stageH, smallAspect, defRaw)
                            persistPip(
                                defRaw.copy(
                                    cx = clamped.normCx,
                                    cy = clamped.normCy,
                                    scale = clamped.normScaleX,
                                    scaleY = clamped.normScaleY
                                ),
                                commitToCamera = false
                            )
                            showToast("Overlay reset")
                        },
                        onTogglePause = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val next = pipLive.copy(paused = !pipLive.paused)
                            persistPip(next, commitToCamera = false)
                            showToast(if (next.paused) "Small camera paused" else "Small camera running")
                        },
                        onClose = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val next = pipLive.copy(visible = false, paused = false)
                            persistPip(next, commitToCamera = false)
                            showToast("Small camera off — main camera only")
                        }
                    )
                } else {
                    // Staplade rutor uppifrån och ner i Separate / Both (exakt som webb-previewn).
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val totalH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                        val showBack = !hideBack
                        val showFront = !hideFront
                        val topIsFront = frontFirst

                        val topVisible = if (topIsFront) showFront else showBack
                        val bottomVisible = if (topIsFront) showBack else showFront
                        val topFrac = when {
                            topVisible && bottomVisible -> if (topIsFront) (1f - split) else split
                            topVisible -> 1f
                            else -> 0f
                        }

                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            val order = if (topIsFront) listOf(true, false) else listOf(false, true)
                            order.forEachIndexed { idx, isFrontCam ->
                                key(if (isFrontCam) "stack_front" else "stack_back") {
                                    val visible = if (isFrontCam) showFront else showBack
                                    if (visible) {
                                        val weightVal = when {
                                            topVisible && bottomVisible -> if (idx == 0) topFrac else (1f - topFrac)
                                            else -> 1f
                                        }.coerceAtLeast(0.05f)
                                        val aspect = if (isFrontCam) effectiveFrontAspect else effectiveBackAspect
                                        val tilePaused = if (isFrontCam) frontPaused else backPaused

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(weightVal)
                                        ) {
                                            FeedTile(
                                                view = if (isFrontCam) frontView else backView,
                                                badge = "${if (isFrontCam) "FRONT" else "BACK"} · ${aspect.shortLabel()}",
                                                zoom = if (isFrontCam) frontZoom else backZoom,
                                                paused = tilePaused,
                                                onZoom = { factor ->
                                                    if (isFrontCam) {
                                                        val (_, fz) = controller.setZoom(front = (frontZoom * factor).coerceIn(1f, 3f))
                                                        frontZoom = fz
                                                    } else {
                                                        val (bz, _) = controller.setZoom(back = (backZoom * factor).coerceIn(1f, 3f))
                                                        backZoom = bz
                                                    }
                                                },
                                                onFocus = { nx, ny ->
                                                    controller.focusAt(
                                                        isFront = isFrontCam,
                                                        nx = nx,
                                                        ny = ny,
                                                        view = if (isFrontCam) frontView else backView
                                                    )
                                                },
                                                onRelease = { v ->
                                                    if (isFrontCam) {
                                                        if (frontView === v) frontView = null
                                                    } else {
                                                        if (backView === v) backView = null
                                                    }
                                                },
                                                onView = { v ->
                                                    if (isFrontCam) frontView = v else backView = v
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )

                                            if (isRecording && (showBack && showFront)) {
                                                TileChips(
                                                    paused = tilePaused,
                                                    canHide = true,
                                                    onPause = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        val camName = if (isFrontCam) "FRONT" else "BACK"
                                                        if (tilePaused) {
                                                            if (controller.resumeCamera(back = !isFrontCam)) {
                                                                if (isFrontCam) frontPaused = false else backPaused = false
                                                                status = "$camName resumed — same file"
                                                                showToast("$camName resumed")
                                                            }
                                                        } else if (controller.pauseCamera(back = !isFrontCam)) {
                                                            if (isFrontCam) frontPaused = true else backPaused = true
                                                            status = "$camName paused — the other camera keeps recording"
                                                            showToast("$camName paused")
                                                        }
                                                    },
                                                    onHide = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        val camName = if (isFrontCam) "FRONT" else "BACK"
                                                        if (isFrontCam) {
                                                            hideFront = true; frontPaused = false
                                                        } else {
                                                            hideBack = true; backPaused = false
                                                        }
                                                        controller.stopCamera(back = !isFrontCam)
                                                        status = "$camName removed — recording continues"
                                                        showToast("$camName removed")
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (showBack && showFront) {
                            val handleY = (topFrac * totalH - with(LocalDensity.current) { 12.dp.toPx() }).roundToInt()
                            SplitHandle(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .offset { IntOffset(0, handleY) },
                                onDrag = { dy ->
                                    val nextTop = (topFrac + dy / totalH).coerceIn(0.3f, 0.7f)
                                    split = if (topIsFront) (1f - nextTop) else nextTop
                                }
                            )

                            StackSwapPill(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 10.dp),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    frontFirst = !frontFirst
                                }
                            )
                        }
                    }

                    // Dold yta för sammansatt fil i BOTH-läget.
                    if (effectiveMode == RecordingMode.BOTH) {
                        AndroidView(
                            modifier = Modifier.size(1.dp).alpha(0f),
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                    composedView = this
                                }
                            },
                            onRelease = { if (composedView === it) composedView = null }
                        )
                    }
                }

                if (showGrid) {
                    GridOverlay(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
            }

            // Statusrad + mono-formatsammanfattning (.rec-status)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp)
            ) {
                Text(status, color = DuelTextMuted, fontSize = 12.sp, lineHeight = 16.sp)
                Text(
                    text = buildFormatSummary(
                        single = isSingleCamera,
                        singleFront = controller.singleUseFront,
                        frontAspect = effectiveFrontAspect,
                        backAspect = effectiveBackAspect,
                        quality = effectiveQuality,
                        audio = vm.audioEnabled,
                        dualMode = effectiveMode == RecordingMode.DUAL_VIEW,
                        pipOff = !pipLive.visible,
                        pipPaused = pipLive.paused
                    ),
                    color = DuelTextFaint,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (savedNames.isNotEmpty()) {
                    Text(
                        text = "Saved " + savedNames.joinToString(" · "),
                        color = Color(0xFF8FA0C8),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Enkelradig kameradocka längst ner (.rec-controls)
            val mainZoom = when {
                isSingleCamera -> if (controller.singleUseFront) frontZoom else backZoom
                effectiveMode == RecordingMode.DUAL_VIEW && pipLive.swapCameras -> frontZoom
                else -> backZoom
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 32.dp, end = 32.dp, top = 12.dp, bottom = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockZoomButton(
                    zoom = mainZoom,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val next = when {
                            mainZoom < 1.5f -> 2f
                            mainZoom < 2.5f -> 3f
                            else -> 1f
                        }
                        val useFront = when {
                            isSingleCamera -> controller.singleUseFront
                            effectiveMode == RecordingMode.DUAL_VIEW -> pipLive.swapCameras
                            else -> false
                        }
                        if (useFront) {
                            val (_, hwFz) = controller.setZoom(front = next)
                            frontZoom = if (effectiveMode == RecordingMode.DUAL_VIEW) next else hwFz
                            frontHwZoom = hwFz
                        } else {
                            val (hwBz, _) = controller.setZoom(back = next)
                            backZoom = if (effectiveMode == RecordingMode.DUAL_VIEW) next else hwBz
                            backHwZoom = hwBz
                        }
                        showToast("Zoom ${formatZoom(next)}")
                    }
                )

                RecordButton(
                    recording = isRecording,
                    enabled = ready && controller.canBind,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (!isRecording) {
                            val freeMb = runCatching { context.filesDir.usableSpace / (1024 * 1024) }.getOrDefault(0L)
                            if (freeMb in 1..299) {
                                status = "Low storage: ${freeMb} MB free — free up space first"
                                showToast("Low storage")
                            } else {
                                takeMode = vm.mode
                                takeFrontAspect = vm.frontAspect
                                takeBackAspect = vm.backAspect
                                takeQuality = vm.quality
                                controller.startRecording(
                                    quality = vm.quality,
                                    audioEnabled = vm.audioEnabled,
                                    onStarted = { names ->
                                        savedNames = emptyList()
                                        isRecording = true
                                        paused = false
                                        backPaused = false
                                        frontPaused = false
                                        hideBack = false
                                        hideFront = false
                                        status = "Recording · ${vm.mode.title.lowercase()}" +
                                            if (isSingleCamera) " · single camera" else ""
                                        pendingClipNames = names
                                    },
                                    onError = { err ->
                                        takeMode = null
                                        takeFrontAspect = null
                                        takeBackAspect = null
                                        takeQuality = null
                                        status = err
                                        showToast("Could not start recording")
                                    }
                                )
                            }
                        } else {
                            val finishedCount = pendingClipNames.size.coerceAtLeast(1)
                            val finishedNames = pendingClipNames
                            controller.stopRecording()
                            isRecording = false
                            paused = false
                            backPaused = false
                            frontPaused = false
                            hideBack = false
                            hideFront = false
                            takeMode = null
                            takeFrontAspect = null
                            takeBackAspect = null
                            takeQuality = null
                            savedNames = finishedNames
                            status = if (finishedCount == 1) "Saved to Gallery" else "Saved $finishedCount clips to Gallery"
                            showToast(if (finishedCount == 1) "Saved to Gallery" else "Saved $finishedCount clips")
                        }
                    }
                )

                if (!isSingleCamera) {
                    DockIconButton(
                        kind = DuelIconKind.SWAP,
                        active = if (effectiveMode == RecordingMode.DUAL_VIEW) pipLive.swapCameras else !frontFirst,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (effectiveMode == RecordingMode.DUAL_VIEW) {
                                val next = pipLive.copy(swapCameras = !pipLive.swapCameras)
                                persistPip(next, commitToCamera = false)
                                showToast(if (next.swapCameras) "Front camera large" else "Back camera large")
                            } else {
                                frontFirst = !frontFirst
                                showToast("Swapped camera order")
                            }
                        }
                    )

                    DockIconButton(
                        kind = DuelIconKind.PIP,
                        active = effectiveMode == RecordingMode.DUAL_VIEW && !pipLive.visible,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (effectiveMode != RecordingMode.DUAL_VIEW) {
                                vm.mode = RecordingMode.DUAL_VIEW
                                val next = pipLive.copy(visible = true, paused = false)
                                persistPip(next, commitToCamera = false)
                                showToast(if (isRecording) "Dual View starts next take" else "Dual View")
                            } else {
                                val nextVisible = !pipLive.visible
                                val next = pipLive.copy(visible = nextVisible, paused = false)
                                persistPip(next, commitToCamera = false)
                                showToast(
                                    if (nextVisible) "Small camera on"
                                    else "Small camera off — main camera only"
                                )
                            }
                        }
                    )
                } else {
                    DockIconButton(
                        kind = DuelIconKind.SWITCH,
                        active = controller.singleUseFront,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (isRecording) {
                                showToast("Stop recording to switch camera")
                            } else if (controller.canFlipSingle) {
                                controller.flipSingleCamera()
                                singleFlipTick++
                            } else {
                                showToast("Only one camera available")
                            }
                        }
                    )
                    Spacer(Modifier.size(46.dp))
                }
            }
        }

        // Flytande toast-piller (exakt som .toast i webb-previewn)
        toastText?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 116.dp, start = 24.dp, end = 24.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xF5121522))
                    .border(1.dp, DuelLineStrong, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = msg.uppercase(),
                    color = DuelText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 0.8.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Camera setup bottom sheet (samma som mountCameraSheet i webb-previewn)
        if (showSetupSheet) {
            CameraSetupSheet(
                vm = vm,
                isRecording = isRecording,
                onDismiss = { showSetupSheet = false },
                onRequirePremium = { feat -> premiumFeature = feat },
                onResetOverlay = {
                    val next = pipLive.copy(
                        cx = PIP_DEFAULT_CX,
                        cy = PIP_DEFAULT_CY,
                        scale = PIP_DEFAULT_SCALE
                    )
                    persistPip(next, commitToCamera = !isRecording)
                    showToast("Overlay reset")
                },
                onToast = { showToast(it) }
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

private fun AspectOption.shortLabel(): String = label.substringBefore(' ')

private fun QualityOption.shortLabel(): String = when (this) {
    QualityOption.HD720 -> "720P"
    QualityOption.FHD1080 -> "1080P"
    QualityOption.UHD4K -> "4K"
}

private fun formatZoom(z: Float): String {
    val r = (z * 10f).roundToInt() / 10f
    return if (r % 1f == 0f) "${r.toInt()}×" else "${r}×"
}

private fun buildFormatSummary(
    single: Boolean,
    singleFront: Boolean,
    frontAspect: AspectOption,
    backAspect: AspectOption,
    quality: QualityOption,
    audio: Boolean,
    dualMode: Boolean,
    pipOff: Boolean,
    pipPaused: Boolean
): String {
    val audioText = if (audio) "AUDIO" else "MUTED"
    if (single) {
        val cam = if (singleFront) "FRONT" else "BACK"
        val a = if (singleFront) frontAspect.shortLabel() else backAspect.shortLabel()
        return "$cam $a · ${quality.shortLabel()} · $audioText"
    }
    val pipSuffix = when {
        !dualMode -> ""
        pipOff -> " · SMALL OFF"
        pipPaused -> " · SMALL PAUSED"
        else -> ""
    }
    return "FRONT ${frontAspect.shortLabel()} · BACK ${backAspect.shortLabel()} · ${quality.shortLabel()} · $audioText$pipSuffix"
}

@Composable
private fun CameraPlaceholder(label: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF05060A)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        DuelIcon(DuelIconKind.CAMERA, DuelTextFaint, 26.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = label.uppercase(),
            color = DuelTextFaint,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun FeedTile(
    view: PreviewView?,
    badge: String?,
    zoom: Float = 1f,
    paused: Boolean = false,
    onZoom: ((Float) -> Unit)? = null,
    onFocus: ((Float, Float) -> Unit)? = null,
    onRelease: (PreviewView) -> Unit = {},
    onView: (PreviewView) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF05060A))
    ) {
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
            var focusPoint by remember { mutableStateOf<Offset?>(null) }
            val haptic = LocalHapticFeedback.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(onZoom, onFocus) {
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

        if (paused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .62f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PAUSED",
                    color = Color.White.copy(alpha = .85f),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp
                )
            }
        }

        if (badge != null) {
            Text(
                text = badge,
                color = DuelOrangeLight,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 0.6.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = .48f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        if (zoom > 1.01f) {
            Text(
                text = formatZoom(zoom),
                color = DuelText,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = .5f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * Realtids OpenGL Dual View-scen där BÅDE videoströmmen och den rundade ramen
 * flyttas, skalas, byter plats och pausas i exakt samma koordinatsystem i 60 fps.
 */
@Composable
private fun DualGlViewStage(
    controller: DualCameraController,
    pipLive: PipLayout,
    smallAspect: AspectOption,
    mainZoom: Float,
    smallZoom: Float,
    backShaderZoom: Float,
    frontShaderZoom: Float,
    showPipHint: Boolean,
    onUpdateLive: (PipLayout) -> Unit,
    onCommitLive: (PipLayout) -> Unit,
    onMainZoom: (Float) -> Unit,
    onSmallZoom: (Float) -> Unit,
    onMainFocus: (Float, Float) -> Unit,
    onSmallFocus: (Float, Float) -> Unit,
    onReset: (Float, Float) -> Unit,
    onTogglePause: () -> Unit,
    onClose: () -> Unit
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var activePipGesture by remember { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF05060A))
    ) {
        val stageW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val stageH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val rect = computePipRect(stageW, stageH, smallAspect, pipLive)
        val cornerNorm = with(density) { 10.dp.toPx() } / stageW

        // Skicka exakta bild-i-bild-koordinater till OpenGL-kompositören på varje frame.
        SideEffect {
            controller.updateDualScene(
                DualGlComposer.SceneState(
                    pipLeftNorm = rect.left / stageW,
                    pipTopNorm = rect.top / stageH,
                    pipWidthNorm = rect.width / stageW,
                    pipHeightNorm = rect.height / stageH,
                    pipCxNorm = rect.normCx,
                    pipCyNorm = rect.normCy,
                    pipCornerNorm = cornerNorm,
                    smallAspectRatio = smallAspect.ratio(),
                    swapCameras = pipLive.swapCameras,
                    pipVisible = pipLive.visible,
                    pipPaused = pipLive.paused,
                    backZoom = backShaderZoom,
                    frontZoom = frontShaderZoom
                )
            )
        }

        // 1. Live sammansatt OpenGL-vy (både stor + liten kamera renderas här)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TextureView(ctx).also { tv ->
                    controller.attachDualTextureView(tv)
                }
            }
        )

        // 2. Stationärt gestlager i fasta scenkoordinater (eliminerar gummiband/förskjutning vid drag)
        val livePipState = rememberUpdatedState(pipLive)
        val liveRectState = rememberUpdatedState(rect)
        val liveAspectState = rememberUpdatedState(smallAspect)
        val updateCb = rememberUpdatedState(onUpdateLive)
        val commitCb = rememberUpdatedState(onCommitLive)
        val mainZoomCb = rememberUpdatedState(onMainZoom)
        val smallZoomCb = rememberUpdatedState(onSmallZoom)
        val mainFocusCb = rememberUpdatedState(onMainFocus)
        val smallFocusCb = rememberUpdatedState(onSmallFocus)
        val resetCb = rememberUpdatedState(onReset)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(stageW, stageH) {
                    var lastPipTapAt = 0L
                    val resizeHitPx = 42.dp.toPx()
                    val resizePadPx = 14.dp.toPx()

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        val startPip = livePipState.value
                        val startRect = liveRectState.value
                        val aspect = liveAspectState.value
                        val ratio = aspect.ratio()
                        val downPos = down.position

                        val pipRight = startRect.left + startRect.width
                        val pipBottom = startRect.top + startRect.height

                        val onResizeCorner = startPip.visible &&
                            downPos.x in (pipRight - resizeHitPx)..(pipRight + resizePadPx) &&
                            downPos.y in (pipBottom - resizeHitPx)..(pipBottom + resizePadPx)

                        val insidePip = startPip.visible &&
                            downPos.x in startRect.left..pipRight &&
                            downPos.y in startRect.top..pipBottom

                        when {
                            onResizeCorner -> {
                                activePipGesture = true
                                val anchorLeft = startRect.left
                                val anchorTop = startRect.top
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val ch = event.changes.firstOrNull { it.id == down.id }
                                        ?: event.changes.firstOrNull()
                                    if (ch != null && ch.pressed) {
                                        val dx = ch.position.x - downPos.x
                                        val dy = ch.position.y - downPos.y
                                        val deltaW = (dx + dy * ratio) * 0.5f
                                        val rawScale = ((startRect.width + deltaW) / stageW)
                                            .coerceIn(PIP_MIN, PIP_MAX)
                                        var pw = stageW * rawScale
                                        var ph = pw / ratio
                                        val maxH = stageH * 0.72f
                                        val maxW = stageW * 0.56f
                                        if (ph > maxH) { ph = maxH; pw = ph * ratio }
                                        if (pw > maxW) { pw = maxW; ph = pw / ratio }

                                        val rawCx = ((anchorLeft + pw / 2f) / stageW).coerceIn(0.05f, 0.95f)
                                        val rawCy = ((anchorTop + ph / 2f) / stageH).coerceIn(0.05f, 0.95f)
                                        val cur = livePipState.value
                                        val clamped = computePipRect(
                                            stageW,
                                            stageH,
                                            aspect,
                                            cur.copy(cx = rawCx, cy = rawCy, scale = pw / stageW)
                                        )
                                        updateCb.value(
                                            cur.copy(
                                                cx = clamped.normCx,
                                                cy = clamped.normCy,
                                                scale = clamped.normScaleX,
                                                scaleY = clamped.normScaleY
                                            )
                                        )
                                    }
                                    event.changes.forEach { it.consume() }
                                    if (event.changes.none { it.pressed }) break
                                }
                                activePipGesture = false
                                commitCb.value(livePipState.value)
                            }

                            insidePip -> {
                                activePipGesture = true
                                val grabOffsetX = downPos.x - (startRect.left + startRect.width / 2f)
                                val grabOffsetY = downPos.y - (startRect.top + startRect.height / 2f)
                                var moved = false
                                var pinching = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (event.changes.size >= 2) {
                                        val zFactor = event.calculateZoom()
                                        if (zFactor != 1f) {
                                            pinching = true
                                            smallZoomCb.value(zFactor)
                                        }
                                    } else if (!pinching && pressed.size == 1) {
                                        val pos = pressed[0].position
                                        if (kotlin.math.hypot(pos.x - downPos.x, pos.y - downPos.y) > 5f) {
                                            moved = true
                                        }
                                        if (moved) {
                                            val targetCx = ((pos.x - grabOffsetX) / stageW).coerceIn(0.05f, 0.95f)
                                            val targetCy = ((pos.y - grabOffsetY) / stageH).coerceIn(0.05f, 0.95f)
                                            val cur = livePipState.value
                                            val clamped = computePipRect(
                                                stageW,
                                                stageH,
                                                aspect,
                                                cur.copy(cx = targetCx, cy = targetCy)
                                            )
                                            updateCb.value(
                                                cur.copy(
                                                    cx = clamped.normCx,
                                                    cy = clamped.normCy,
                                                    scale = clamped.normScaleX,
                                                    scaleY = clamped.normScaleY
                                                )
                                            )
                                        }
                                    }
                                    event.changes.forEach { it.consume() }
                                    if (pressed.isEmpty()) break
                                }
                                activePipGesture = false
                                if (moved) {
                                    commitCb.value(livePipState.value)
                                    lastPipTapAt = 0L
                                } else if (!pinching) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastPipTapAt < 320L) {
                                        resetCb.value(stageW, stageH)
                                        lastPipTapAt = 0L
                                    } else {
                                        lastPipTapAt = now
                                        focusPoint = downPos
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        smallFocusCb.value(
                                            (downPos.x - startRect.left) / startRect.width.coerceAtLeast(1f),
                                            (downPos.y - startRect.top) / startRect.height.coerceAtLeast(1f)
                                        )
                                    }
                                }
                            }

                            else -> {
                                var moved = false
                                var pinching = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (event.changes.size >= 2) {
                                        val zFactor = event.calculateZoom()
                                        if (zFactor != 1f) {
                                            pinching = true
                                            mainZoomCb.value(zFactor)
                                        }
                                    } else if (!pinching && pressed.size == 1) {
                                        val pos = pressed[0].position
                                        if (kotlin.math.hypot(pos.x - downPos.x, pos.y - downPos.y) > 12f) {
                                            moved = true
                                        }
                                    }
                                    event.changes.forEach { it.consume() }
                                    if (pressed.isEmpty()) break
                                }
                                if (!moved && !pinching) {
                                    focusPoint = downPos
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    mainFocusCb.value(downPos.x / stageW, downPos.y / stageH)
                                }
                            }
                        }
                    }
                }
        )

        // 3. Fokusring vid tryck
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

        // 4. Huvudkamerans brickor (BACK/FRONT + zoom)
        Text(
            text = if (pipLive.swapCameras) "FRONT" else "BACK",
            color = DuelOrangeLight,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 0.6.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = .48f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )

        if (mainZoom > 1.01f) {
            Text(
                text = formatZoom(mainZoom),
                color = DuelText,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = .5f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // 5. Lilla kamerans ram, paus/stäng-knappar och visuellt storlekshandtag
        if (pipLive.visible) {
            PipChromeBox(
                rect = rect,
                activeGesture = activePipGesture,
                paused = pipLive.paused,
                zoom = smallZoom,
                onTogglePause = onTogglePause,
                onClose = onClose
            )

            if (showPipHint) {
                PipHintPill(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun PipChromeBox(
    rect: PipBoxRect,
    activeGesture: Boolean,
    paused: Boolean,
    zoom: Float,
    onTogglePause: () -> Unit,
    onClose: () -> Unit
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (activeGesture) 1.6.dp else 1.dp,
                color = if (activeGesture) DuelGold else Color.White.copy(alpha = .62f),
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        if (paused) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PAUSED",
                    color = Color.White.copy(alpha = .88f),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp
                )
            }
        }

        if (zoom > 1.01f) {
            Text(
                text = formatZoom(zoom),
                color = DuelText,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = .52f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }

        // Paus / Stäng uppe till vänster (konsumerar endast tryck på dessa två cirklar)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            PipSmallButton(if (paused) DuelIconKind.PLAY else DuelIconKind.PAUSE, onClick = onTogglePause)
            PipSmallButton(DuelIconKind.CLOSE, onClick = onClose)
        }

        // Visuellt diagonalt storlekshandtag nere till höger
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(26.dp)
                .clip(RoundedCornerShape(topStart = 8.dp))
                .background(Color.Black.copy(alpha = .5f))
                .border(
                    width = 1.dp,
                    color = if (activeGesture) DuelGold.copy(alpha = .75f) else Color.White.copy(alpha = .28f),
                    shape = RoundedCornerShape(topStart = 8.dp)
                )
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawLine(
                    color = Color.White.copy(alpha = .6f),
                    start = Offset(w, h * 0.24f),
                    end = Offset(w * 0.24f, h),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Square
                )
                drawLine(
                    color = Color.White.copy(alpha = .92f),
                    start = Offset(w, h * 0.56f),
                    end = Offset(w * 0.56f, h),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Square
                )
            }
        }
    }
}

@Composable
private fun PipHintPill(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xD6121522))
            .border(1.dp, DuelLine, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = "DRAG TO MOVE · PINCH TO ZOOM · CORNER TO RESIZE",
            color = DuelTextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.5.sp,
            letterSpacing = 0.6.sp
        )
    }
}

@Composable
private fun PipSmallButton(kind: DuelIconKind, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(25.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = .58f))
            .border(1.dp, Color.White.copy(alpha = .30f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        DuelIcon(kind, Color.White, 13.dp)
    }
}

@Composable
private fun TileChips(
    paused: Boolean,
    canHide: Boolean,
    onPause: () -> Unit,
    onHide: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = .58f))
                    .border(1.dp, Color.White.copy(alpha = .32f), CircleShape)
                    .clickable(onClick = onPause),
                contentAlignment = Alignment.Center
            ) {
                DuelIcon(if (paused) DuelIconKind.PLAY else DuelIconKind.PAUSE, Color.White, 14.dp)
            }
            if (canHide) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = .58f))
                        .border(1.dp, Color.White.copy(alpha = .32f), CircleShape)
                        .clickable(onClick = onHide),
                    contentAlignment = Alignment.Center
                ) {
                    DuelIcon(DuelIconKind.CLOSE, Color.White, 14.dp)
                }
            }
        }
    }
}

@Composable
private fun SplitHandle(modifier: Modifier, onDrag: (Float) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    val dragCb = rememberUpdatedState(onDrag)
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 24.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false }
                ) { change, drag ->
                    change.consume()
                    dragCb.value(drag.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 34.dp, height = 5.dp)
                .clip(CircleShape)
                .background(if (dragging) DuelOrangeLight else Color.White.copy(alpha = .55f))
        )
    }
}

@Composable
private fun StackSwapPill(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xDC121522))
            .border(1.dp, DuelLineStrong, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DuelIcon(DuelIconKind.SWAP, DuelText, 13.dp)
        Text(
            text = "SWAP",
            color = DuelText,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun GridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val line = Color.White.copy(alpha = .26f)
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

@Composable
private fun IconChip(kind: DuelIconKind, active: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .then(
                if (active) Modifier.background(DuelGradient)
                else Modifier
                    .background(DuelSurface2)
                    .border(1.dp, DuelLine, CircleShape)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        DuelIcon(kind, if (active) Ink else DuelText, 18.dp)
    }
}

@Composable
private fun TimerPill(seconds: Int, recording: Boolean, paused: Boolean) {
    val blink = rememberInfiniteTransition(label = "rec_dot")
    val dotAlpha by blink.animateFloat(
        initialValue = 1f,
        targetValue = if (recording && !paused) 0.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (recording) DuelRecord.copy(alpha = .16f) else DuelSurface)
            .border(1.dp, if (recording) DuelRecord.copy(alpha = .5f) else DuelLine, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(if (recording && !paused) dotAlpha else 1f)
                .clip(CircleShape)
                .background(if (recording) DuelRecord else DuelTextMuted)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60),
            color = DuelText,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun RecordButton(recording: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val innerSize by animateDpAsState(targetValue = if (recording) 30.dp else 66.dp, label = "shutter_size")
    val innerCorner by animateDpAsState(targetValue = if (recording) 8.dp else 33.dp, label = "shutter_corner")

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .border(
                width = 3.dp,
                color = if (enabled) Color.White.copy(alpha = .9f) else Color.White.copy(alpha = .28f),
                shape = CircleShape
            )
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(RoundedCornerShape(innerCorner))
                .background(if (enabled) DuelRecord else DuelRecord.copy(alpha = .35f))
        )
    }
}

@Composable
private fun DockZoomButton(zoom: Float, onClick: () -> Unit) {
    val active = zoom > 1.01f
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(DuelSurface2)
            .border(1.dp, if (active) DuelOrange.copy(alpha = .55f) else DuelLine, CircleShape)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = formatZoom(zoom),
            color = if (active) DuelOrangeLight else DuelTextMuted,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun DockIconButton(kind: DuelIconKind, active: Boolean = false, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(DuelSurface2)
            .border(1.dp, if (active) DuelOrange.copy(alpha = .55f) else DuelLine, CircleShape)
            .clickable(onClick = onClick)
    ) {
        DuelIcon(kind, if (active) DuelOrangeLight else DuelTextMuted, 20.dp)
    }
}

/**
 * Bottom sheet för kamerainställningar direkt under filmning (samma som Camera setup i webb-previewn).
 */
@Composable
private fun CameraSetupSheet(
    vm: AppViewModel,
    isRecording: Boolean,
    onDismiss: () -> Unit,
    onRequirePremium: (String) -> Unit,
    onResetOverlay: () -> Unit,
    onToast: (String) -> Unit
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val backdropInteraction = remember { MutableInteractionSource() }
    val sheetInteraction = remember { MutableInteractionSource() }

    val modeDescriptions = remember {
        mapOf(
            RecordingMode.DUAL_VIEW to "Both cameras composed into one video — back feed full frame, front feed inset.",
            RecordingMode.SEPARATE to "Two files, one per camera. Trim and post each angle independently.",
            RecordingMode.BOTH to "The composed video plus both individual files — three clips in one take."
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .62f))
            .clickable(
                interactionSource = backdropInteraction,
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.90f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(DuelBg2)
                .border(1.dp, DuelLineStrong, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(
                    interactionSource = sheetInteraction,
                    indication = null,
                    onClick = {}
                )
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 22.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 32.dp, height = 3.dp)
                    .clip(CircleShape)
                    .background(DuelLineStrong)
            )
            Spacer(Modifier.height(14.dp))
            Text("Camera setup", color = DuelText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                text = "Frame each camera the way you want it. Both formats are used for the take.",
                color = DuelTextMuted,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 6.dp)
            )

            if (isRecording) {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DuelOrange.copy(alpha = .12f))
                        .border(1.dp, DuelOrange.copy(alpha = .35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 11.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = "ONE TAKE = ONE FILE · CHANGES APPLY TO THE NEXT TAKE",
                        color = DuelOrangeLight,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 0.6.sp
                    )
                }
            }

            SheetSectionLabel("MODE")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RecordingMode.values().forEach { m ->
                    val active = vm.mode == m
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(DuelSurface)
                            .border(
                                width = 1.dp,
                                color = if (active) DuelAccent.copy(alpha = .55f) else DuelLine,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                vm.mode = m
                                if (isRecording) onToast("Mode applies to the next take")
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(m.title, color = DuelText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                text = modeDescriptions[m] ?: m.subtitle,
                                color = DuelTextMuted,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        if (active) {
                            Spacer(Modifier.width(10.dp))
                            DuelIcon(DuelIconKind.CHECK, DuelAccent, 16.dp)
                        }
                    }
                }
            }

            SheetSectionLabel("FRONT CAMERA")
            SheetChipRow(
                items = AspectOption.values().map { it.shortLabel() },
                selectedIndex = vm.frontAspect.ordinal,
                onSelect = { idx ->
                    val chosen = AspectOption.values()[idx]
                    vm.frontAspect = chosen
                    if (isRecording) onToast("Front ${chosen.shortLabel()} · starts next take")
                }
            )

            SheetSectionLabel("BACK CAMERA")
            SheetChipRow(
                items = AspectOption.values().map { it.shortLabel() },
                selectedIndex = vm.backAspect.ordinal,
                onSelect = { idx ->
                    val chosen = AspectOption.values()[idx]
                    vm.backAspect = chosen
                    if (isRecording) onToast("Back ${chosen.shortLabel()} · starts next take")
                }
            )

            SheetSectionLabel("VIDEO QUALITY & AUDIO")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QualityOption.values().forEach { q ->
                    val active = vm.quality == q
                    val short = when (q) {
                        QualityOption.HD720 -> "720p"
                        QualityOption.FHD1080 -> "1080p"
                        QualityOption.UHD4K -> "4K"
                    }
                    SheetPillChip(
                        label = short,
                        active = active,
                        onClick = {
                            if (q.premium && !vm.isPremium) {
                                onRequirePremium(q.label)
                            } else {
                                vm.quality = q
                                if (isRecording) onToast("$short · starts next take")
                            }
                        }
                    )
                }
                SheetPillChip(
                    label = if (vm.audioEnabled) "Audio" else "Muted",
                    active = vm.audioEnabled,
                    onClick = {
                        vm.audioEnabled = !vm.audioEnabled
                        onToast(if (vm.audioEnabled) "Audio on" else "Microphone muted")
                    }
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniActionButton("Swap front and back") {
                    val prevFront = vm.frontAspect
                    vm.frontAspect = vm.backAspect
                    vm.backAspect = prevFront
                    onToast(if (isRecording) "Swapped · starts next take" else "Swapped camera formats")
                }
                if (vm.mode == RecordingMode.DUAL_VIEW) {
                    MiniActionButton("Reset overlay") {
                        onResetOverlay()
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DuelGradient)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Done",
                    color = Ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    letterSpacing = 0.4.sp
                )
            }
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        color = DuelTextMuted,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun SheetChipRow(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEachIndexed { idx, label ->
            val active = idx == selectedIndex
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) DuelAccent.copy(alpha = .16f) else DuelSurface2)
                    .border(
                        width = 1.dp,
                        color = if (active) DuelAccent.copy(alpha = .55f) else DuelLine,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(idx) }
                    .padding(vertical = 9.dp)
            ) {
                Text(
                    text = label,
                    color = if (active) DuelText else DuelTextMuted,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SheetPillChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) DuelAccent.copy(alpha = .16f) else DuelSurface2)
            .border(
                width = 1.dp,
                color = if (active) DuelAccent.copy(alpha = .55f) else DuelLine,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            color = if (active) DuelText else DuelTextMuted,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun MiniActionButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, DuelLineStrong, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = label.uppercase(),
            color = DuelTextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp,
            letterSpacing = 0.7.sp
        )
    }
}
