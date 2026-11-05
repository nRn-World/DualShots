package com.nrworld.duelshot.camera

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Rational
import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CompositionSettings
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.nrworld.duelshot.model.AspectOption
import com.nrworld.duelshot.model.QualityOption
import com.nrworld.duelshot.model.RecordingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Bild-i-bild-placering för Dual View. [cx] och [cy] är mitten av den lilla
 * rutan som andel av bildytan (0..1), [scale] och [scaleY] är bredd/höjd som
 * andel av ytan, och [swapCameras] gör frontkameran stor och bakkameran liten.
 */
data class PipLayout(
    val cx: Float = 0.74f,
    val cy: Float = 0.24f,
    val scale: Float = 0.34f,
    val scaleY: Float = 0.34f,
    val swapCameras: Boolean = false,
    /** false = lilla kameran avstängd; bara stora kameran syns i inspelningen. */
    val visible: Boolean = true,
    /** true = lilla kameran pausad; dess ruta döljs i inspelningen. */
    val paused: Boolean = false
)

/**
 * Styr dubbla kameror (fram + bak) och inspelning i tre lägen.
 * Alltid robust: om hårdvarusammansättning (CompositionSettings) inte stöds av
 * enheten behålls Dual View-gränssnittet med live bild-i-bild-överlägg samtidigt
 * som kamerorna spelar in var sin ström.
 */
class DualCameraController(private val context: Context) {

    private var provider: ProcessCameraProvider? = null
    private var backSelector: CameraSelector? = null
    private var frontSelector: CameraSelector? = null

    // Inspelnings-ytor
    private var backVideo: VideoCapture<Recorder>? = null
    private var frontVideo: VideoCapture<Recorder>? = null
    private var composedVideo: VideoCapture<Recorder>? = null

    private val activeRecordings = mutableListOf<Recording>()
    private val stopWaiters = mutableListOf<() -> Unit>()

    // En Recording per kamera så att en kamera kan pausas eller klickas bort
    // medan den andra fortsätter spela in.
    private var backRecording: Recording? = null
    private var frontRecording: Recording? = null
    private var composedRecording: Recording? = null

    /** Beskärning per kamera via ViewPort; stängs av om enheten inte klarar den. */
    private var cropEnabled = true

    // Zoom per kamera (1× = av). Gäller både förhandsvisning och inspelning.
    private var backCamera: Camera? = null
    private var frontCamera: Camera? = null
    private var backZoom = 1f
    private var frontZoom = 1f

    /** Sätter zoom på bak-/frontkameran och returnerar värdena som faktiskt gick igenom. */
    fun setZoom(back: Float? = null, front: Float? = null): Pair<Float, Float> {
        if (back != null) backZoom = back
        if (front != null) frontZoom = front
        backZoom = applyZoom(backCamera, backZoom)
        frontZoom = applyZoom(frontCamera, frontZoom)
        return backZoom to frontZoom
    }

    private fun applyZoom(camera: Camera?, ratio: Float): Float {
        if (camera == null) return ratio
        val state = camera.cameraInfo.zoomState.value ?: return ratio
        val target = ratio.coerceIn(state.minZoomRatio, state.maxZoomRatio)
        runCatching { camera.cameraControl.setZoomRatio(target) }
        return target
    }

    private fun rememberCameras(cameras: List<Camera>) {
        backCamera = null
        frontCamera = null
        cameras.forEach { cam ->
            when (cam.cameraInfo.lensFacing) {
                CameraSelector.LENS_FACING_BACK -> backCamera = cam
                CameraSelector.LENS_FACING_FRONT -> frontCamera = cam
            }
        }
        // Behåll zoomen över ombindningar (t.ex. när den lilla rutan flyttas).
        backZoom = applyZoom(backCamera, backZoom)
        frontZoom = applyZoom(frontCamera, frontZoom)
    }

    /** Vilket inspelningsläge som faktiskt band under huven. */
    var activeMode = RecordingMode.DUAL_VIEW
        private set
    var supported = false
        private set

    /** Minst en kamera går att använda (dubbel eller enskild). */
    var canBind = false
        private set

    /** Sant när dubbla kameror inte stöds och vi föll tillbaka till en kamera. */
    var singleFallback = false
        private set

    /** Vilken kamera som visas i single-kamera-läge. */
    var singleUseFront = false
        private set

    /** Har enheten både fram- och bakkamera att växla mellan i single-läge? */
    val canFlipSingle: Boolean
        get() = backSelector != null && frontSelector != null

    /**
     * Sant när användaren valt Dual View på en telefon med dubbla kameror där
     * CameraX CompositionSettings inte stöds: vi visar då live bild-i-bild i UI
     * med båda kamerorna samtidigt och sparar strömmarna som egna MP4-filer.
     */
    var dualOverlayFallback = false
        private set

    /** Senaste felet från en misslyckad sammansättning, för statusraden. */
    var lastBindError: String? = null
        private set

    fun flipSingleCamera(): Boolean {
        if (!canFlipSingle) return singleUseFront
        singleUseFront = !singleUseFront
        return singleUseFront
    }

    suspend fun init(): String {
        return try {
            val p = ProcessCameraProvider.getInstance(context.applicationContext).get()
            provider = p
            for (infos in p.availableConcurrentCameraInfos) {
                val back = infos.firstOrNull { it.lensFacing == CameraSelector.LENS_FACING_BACK }?.cameraSelector
                val front = infos.firstOrNull { it.lensFacing == CameraSelector.LENS_FACING_FRONT }?.cameraSelector
                if (back != null && front != null) {
                    backSelector = back; frontSelector = front; supported = true; break
                }
            }
            if (!supported) {
                // Säkerställ enskilda selectorer så att single-kamera-läge alltid fungerar.
                if (backSelector == null) backSelector = pick(p, CameraSelector.DEFAULT_BACK_CAMERA)
                if (frontSelector == null) frontSelector = pick(p, CameraSelector.DEFAULT_FRONT_CAMERA)
            }
            canBind = backSelector != null || frontSelector != null
            when {
                supported -> "Dual View · front + back"
                canBind -> "Single camera · ${if (backSelector != null) "back" else "front"}"
                else -> "No camera available on this device"
            }
        } catch (e: Exception) {
            "Could not start camera: ${e.localizedMessage}"
        }
    }

    private fun pick(p: ProcessCameraProvider, sel: CameraSelector): CameraSelector? =
        runCatching { if (p.hasCamera(sel)) sel else null }.getOrNull()

    /**
     * Binder kamerorna enligt [wanted] och returnerar statusmeddelande.
     */
    fun bind(
        wanted: RecordingMode,
        frontAspect: AspectOption,
        backAspect: AspectOption,
        quality: QualityOption,
        lifecycleOwner: LifecycleOwner,
        backView: PreviewView?,
        frontView: PreviewView?,
        composedView: PreviewView?,
        pip: PipLayout = PipLayout(),
        forceOverlayPip: Boolean = false
    ): String {
        val p = provider ?: return "Starting cameras…"

        p.unbindAll()
        backVideo = null; frontVideo = null; composedVideo = null
        backCamera = null; frontCamera = null
        singleFallback = false
        dualOverlayFallback = false

        val qs = qualitySelector(quality)

        // Dubbla kameror kräver båda selectorerna; annars enskild kamera som fallback.
        if (!supported || backSelector == null || frontSelector == null) {
            return bindSingle(p, frontAspect, backAspect, qs, lifecycleOwner, backView, frontView)
        }
        val bs = backSelector!!
        val fs = frontSelector!!

        return when (wanted) {
            RecordingMode.DUAL_VIEW -> {
                if (!forceOverlayPip) {
                    val composedAttempt = runCatching {
                        bindComposed(p, bs, fs, qs, lifecycleOwner, composedView ?: backView, pip)
                    }.recoverCatching {
                        p.unbindAll()
                        bindComposed(p, bs, fs, hdSelector(), lifecycleOwner, composedView ?: backView, pip)
                    }
                    if (composedAttempt.isSuccess) {
                        activeMode = RecordingMode.DUAL_VIEW
                        dualOverlayFallback = false
                        return "Dual View · front + back"
                    }
                    val err = composedAttempt.exceptionOrNull()
                    lastBindError = (err?.localizedMessage ?: err?.javaClass?.simpleName ?: "unsupported").take(120)
                    runCatching { p.unbindAll() }
                }

                // Hårdvarusammansättning stöds inte på enheten: behåll Dual View med
                // live bild-i-bild i gränssnittet och bind båda kameraströmmarna direkt.
                dualOverlayFallback = true
                activeMode = RecordingMode.SEPARATE
                if (backView != null && frontView != null) {
                    return try {
                        bindSeparateRobust(p, bs, fs, frontAspect, backAspect, qs, lifecycleOwner, backView, frontView)
                        "Dual View · front + back"
                    } catch (e: Exception) {
                        runCatching { p.unbindAll() }
                        bindSingle(p, frontAspect, backAspect, qs, lifecycleOwner, backView, frontView)
                    }
                }
                "Dual View · front + back"
            }

            RecordingMode.SEPARATE -> {
                try {
                    bindSeparateRobust(p, bs, fs, frontAspect, backAspect, qs, lifecycleOwner, backView, frontView)
                    activeMode = RecordingMode.SEPARATE
                    "Separate Videos · front + back"
                } catch (e: Exception) {
                    runCatching { p.unbindAll() }
                    bindSingle(p, frontAspect, backAspect, qs, lifecycleOwner, backView, frontView)
                }
            }

            RecordingMode.BOTH -> {
                val bothAttempt = runCatching {
                    bindBoth(p, bs, fs, frontAspect, backAspect, qs, lifecycleOwner, backView, frontView, composedView, pip)
                }.recoverCatching {
                    p.unbindAll()
                    bindBoth(p, bs, fs, frontAspect, backAspect, hdSelector(), lifecycleOwner, backView, frontView, composedView, pip)
                }
                if (bothAttempt.isSuccess) {
                    activeMode = RecordingMode.BOTH
                    "Both Options · front + back"
                } else {
                    runCatching { p.unbindAll() }
                    try {
                        bindSeparateRobust(p, bs, fs, frontAspect, backAspect, qs, lifecycleOwner, backView, frontView)
                        activeMode = RecordingMode.SEPARATE
                        "Both Options · front + back (2 files)"
                    } catch (e: Exception) {
                        runCatching { p.unbindAll() }
                        bindSingle(p, frontAspect, backAspect, qs, lifecycleOwner, backView, frontView)
                    }
                }
            }
        }
    }

    private fun bindSingle(
        p: ProcessCameraProvider,
        frontAspect: AspectOption, backAspect: AspectOption, qs: QualitySelector,
        owner: LifecycleOwner, backView: PreviewView?, frontView: PreviewView?
    ): String {
        val hasBack = backSelector != null
        val hasFront = frontSelector != null
        val useFront = when {
            singleUseFront && hasFront -> true
            !singleUseFront && hasBack -> false
            hasBack -> false
            else -> true
        }
        singleUseFront = useFront
        val sel = (if (useFront) frontSelector else backSelector)
            ?: return "No camera available"
        return try {
            p.unbindAll()
            val view = if (useFront) (frontView ?: backView) else (backView ?: frontView)
            val aspect = if (useFront) frontAspect else backAspect
            val (prev, vc) = previewAndCapture(view, aspect, qs)
            rememberCameras(listOf(p.bindToLifecycle(owner, sel, groupOf(aspect, prev, vc))))
            if (useFront) frontVideo = vc else backVideo = vc
            activeMode = RecordingMode.SEPARATE
            singleFallback = true
            "Single camera · ${if (useFront) "front" else "back"}"
        } catch (e: Exception) {
            if (cropEnabled) {
                cropEnabled = false
                bindSingle(p, frontAspect, backAspect, qs, owner, backView, frontView)
            } else "Could not bind camera: ${e.localizedMessage}"
        }
    }

    private fun bindSeparateRobust(
        p: ProcessCameraProvider, bs: CameraSelector, fs: CameraSelector,
        frontAspect: AspectOption, backAspect: AspectOption, qs: QualitySelector,
        owner: LifecycleOwner, backView: PreviewView?, frontView: PreviewView?
    ) {
        val first = runCatching {
            bindSeparate(p, bs, fs, frontAspect, backAspect, qs, owner, backView, frontView)
        }
        if (first.isSuccess) return

        if (cropEnabled) {
            cropEnabled = false
            p.unbindAll()
            val noCrop = runCatching {
                bindSeparate(p, bs, fs, frontAspect, backAspect, qs, owner, backView, frontView)
            }
            if (noCrop.isSuccess) return
        }

        p.unbindAll()
        bindSeparate(p, bs, fs, frontAspect, backAspect, hdSelector(), owner, backView, frontView)
    }

    private fun bindSeparate(
        p: ProcessCameraProvider, bs: CameraSelector, fs: CameraSelector,
        frontAspect: AspectOption, backAspect: AspectOption, qs: QualitySelector,
        owner: LifecycleOwner, backView: PreviewView?, frontView: PreviewView?
    ) {
        val (backPrev, backVc) = previewAndCapture(backView, backAspect, qs)
        val (frontPrev, frontVc) = previewAndCapture(frontView, frontAspect, qs)
        backVideo = backVc; frontVideo = frontVc
        val backGroup = groupOf(backAspect, backPrev, backVc)
        val frontGroup = groupOf(frontAspect, frontPrev, frontVc)
        rememberCameras(
            p.bindToLifecycle(
                listOf(
                    ConcurrentCamera.SingleCameraConfig(bs, backGroup, owner),
                    ConcurrentCamera.SingleCameraConfig(fs, frontGroup, owner)
                )
            ).cameras
        )
    }

    private fun bindComposed(
        p: ProcessCameraProvider, bs: CameraSelector, fs: CameraSelector,
        qs: QualitySelector,
        owner: LifecycleOwner,
        composedView: PreviewView?, pip: PipLayout
    ) {
        val composedPreview = previewFor(composedView)
        val composed = VideoCapture.withOutput(Recorder.Builder().setQualitySelector(qs).build())
        composedVideo = composed
        val group = UseCaseGroup.Builder()
            .addUseCase(composedPreview).addUseCase(composed)
            .build()
        val largeSel = if (pip.swapCameras) fs else bs
        val smallSel = if (pip.swapCameras) bs else fs
        rememberCameras(
            p.bindToLifecycle(
                listOf(
                    ConcurrentCamera.SingleCameraConfig(largeSel, group, fullComposition(), owner),
                    ConcurrentCamera.SingleCameraConfig(smallSel, group, insetComposition(pip), owner)
                )
            ).cameras
        )
    }

    private fun bindBoth(
        p: ProcessCameraProvider, bs: CameraSelector, fs: CameraSelector,
        frontAspect: AspectOption, backAspect: AspectOption, qs: QualitySelector,
        owner: LifecycleOwner, backView: PreviewView?, frontView: PreviewView?, composedView: PreviewView?,
        pip: PipLayout
    ) {
        val composedPreview = Preview.Builder().build().apply {
            composedView?.let { setSurfaceProvider(it.surfaceProvider) }
        }
        val composed = VideoCapture.withOutput(Recorder.Builder().setQualitySelector(qs).build())
        val (backPrev, backVc) = previewAndCapture(backView, backAspect, qs)
        val (frontPrev, frontVc) = previewAndCapture(frontView, frontAspect, qs)
        composedVideo = composed; backVideo = backVc; frontVideo = frontVc

        val backGroup = UseCaseGroup.Builder()
            .addUseCase(composedPreview).addUseCase(composed).addUseCase(backPrev).addUseCase(backVc).build()
        val frontGroup = UseCaseGroup.Builder()
            .addUseCase(composedPreview).addUseCase(composed).addUseCase(frontPrev).addUseCase(frontVc).build()

        val largeSel = if (pip.swapCameras) fs else bs
        val smallSel = if (pip.swapCameras) bs else fs
        rememberCameras(
            p.bindToLifecycle(
                listOf(
                    ConcurrentCamera.SingleCameraConfig(largeSel, backGroup, fullComposition(), owner),
                    ConcurrentCamera.SingleCameraConfig(smallSel, frontGroup, insetComposition(pip), owner)
                )
            ).cameras
        )
    }

    private fun fullComposition(): CompositionSettings =
        CompositionSettings.Builder().setAlpha(1f).setOffset(0f, 0f).setScale(1f, 1f).build()

    /**
     * CameraX-offset är normaliserad (-1..1, 0 = mitten). NDC har positiv Y uppåt
     * medan skärmen har Y nedåt, så X räknas som (2·cx − 1) och Y som (1 − 2·cy).
     */
    private fun insetComposition(pip: PipLayout): CompositionSettings {
        val sx = pip.scale.coerceIn(0.16f, 0.62f)
        val sy = pip.scaleY.coerceIn(0.12f, 0.78f)
        val limitX = (1f - sx).coerceAtLeast(0f)
        val limitY = (1f - sy).coerceAtLeast(0f)
        val ox = (2f * pip.cx - 1f).coerceIn(-limitX, limitX)
        val oy = (1f - 2f * pip.cy).coerceIn(-limitY, limitY)
        val alpha = if (pip.visible && !pip.paused) 1f else 0f
        return CompositionSettings.Builder()
            .setAlpha(alpha)
            .setOffset(ox, oy)
            .setScale(sx, sy)
            .build()
    }

    private fun groupOf(aspect: AspectOption, vararg useCases: androidx.camera.core.UseCase): UseCaseGroup {
        val b = UseCaseGroup.Builder()
        useCases.forEach { b.addUseCase(it) }
        viewPortFor(aspect)?.let { b.setViewPort(it) }
        return b.build()
    }

    /** Beskär både förhandsvisning och inspelning till valt format (appen är låst i porträtt). */
    private fun viewPortFor(a: AspectOption): ViewPort? {
        if (!cropEnabled) return null
        val ratio = when (a) {
            AspectOption.R_9_16 -> Rational(9, 16)
            AspectOption.R_16_9 -> Rational(16, 9)
            AspectOption.R_1_1 -> Rational(1, 1)
            AspectOption.R_4_3 -> Rational(4, 3)
        }
        return ViewPort.Builder(ratio, Surface.ROTATION_0).setScaleType(ViewPort.FILL_CENTER).build()
    }

    private fun previewFor(view: PreviewView?): Preview =
        Preview.Builder().build().apply { view?.let { setSurfaceProvider(it.surfaceProvider) } }

    private fun previewAndCapture(
        view: PreviewView?, aspect: AspectOption, qs: QualitySelector
    ): Pair<Preview, VideoCapture<Recorder>> {
        val preview = previewFor(view)
        val video = VideoCapture.Builder<Recorder>(
            Recorder.Builder().setQualitySelector(qs).build()
        ).build()
        return preview to video
    }

    fun startRecording(
        part: Int = 1,
        audioEnabled: Boolean = true,
        pip: PipLayout = PipLayout(),
        onStarted: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(context)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + (if (part > 1) "_p$part" else "")
        val started = mutableListOf<Recording>()
        val names = mutableListOf<String>()
        backRecording = null; frontRecording = null; composedRecording = null
        try {
            when (activeMode) {
                RecordingMode.SEPARATE -> {
                    if (singleFallback) {
                        val isBack = backVideo != null
                        val v = backVideo ?: frontVideo
                        val n = if (isBack) "dualshot_back_$stamp" else "dualshot_front_$stamp"
                        v?.let {
                            val rec = it.record(context, n, audio = audioEnabled, executor)
                            started += rec
                            if (isBack) backRecording = rec else frontRecording = rec
                            names += "$n.mp4"
                        }
                    } else if (dualOverlayFallback && !pip.visible) {
                        // Lilla kameran är avstängd i Dual View-fallback: spela bara in huvudkameran.
                        val useFrontMain = pip.swapCameras
                        val v = if (useFrontMain) frontVideo else backVideo
                        val n = if (useFrontMain) "dualshot_front_$stamp" else "dualshot_back_$stamp"
                        v?.let {
                            val rec = it.record(context, n, audio = audioEnabled, executor)
                            started += rec
                            if (useFrontMain) frontRecording = rec else backRecording = rec
                            names += "$n.mp4"
                        }
                    } else {
                        val audioOnFront = dualOverlayFallback && pip.swapCameras
                        backVideo?.let {
                            val rec = it.record(context, "dualshot_back_$stamp", audio = audioEnabled && !audioOnFront, executor)
                            started += rec; backRecording = rec; names += "dualshot_back_$stamp.mp4"
                        }
                        frontVideo?.let {
                            val rec = it.record(context, "dualshot_front_$stamp", audio = audioEnabled && audioOnFront, executor)
                            started += rec; frontRecording = rec; names += "dualshot_front_$stamp.mp4"
                        }
                        if (dualOverlayFallback && pip.paused) {
                            val smallRec = if (pip.swapCameras) backRecording else frontRecording
                            smallRec?.let { runCatching { it.pause() } }
                        }
                    }
                }
                RecordingMode.DUAL_VIEW -> {
                    composedVideo?.let {
                        val rec = it.record(context, "dualshot_combined_$stamp", audio = audioEnabled, executor)
                        started += rec; composedRecording = rec; names += "dualshot_combined_$stamp.mp4"
                    }
                }
                RecordingMode.BOTH -> {
                    composedVideo?.let {
                        val rec = it.record(context, "dualshot_combined_$stamp", audio = audioEnabled, executor)
                        started += rec; composedRecording = rec; names += "dualshot_combined_$stamp.mp4"
                    }
                    backVideo?.let {
                        val rec = it.record(context, "dualshot_back_$stamp", audio = false, executor)
                        started += rec; backRecording = rec; names += "dualshot_back_$stamp.mp4"
                    }
                    frontVideo?.let {
                        val rec = it.record(context, "dualshot_front_$stamp", audio = false, executor)
                        started += rec; frontRecording = rec; names += "dualshot_front_$stamp.mp4"
                    }
                }
            }
            if (started.isEmpty()) {
                onError("Camera stream is not ready yet")
                return
            }
            activeRecordings += started
            onStarted(names)
        } catch (e: Exception) {
            started.forEach { runCatching { it.stop() } }
            onError("Recording did not start: ${e.localizedMessage}")
        }
    }

    /** Pausar pågående inspelning – samma fil fortsätter när man återupptar. */
    fun pauseRecording(): Boolean {
        if (activeRecordings.isEmpty()) return false
        activeRecordings.forEach { runCatching { it.pause() } }
        return true
    }

    /** Återupptar en pausad inspelning i samma fil. */
    fun resumeRecording(): Boolean {
        if (activeRecordings.isEmpty()) return false
        activeRecordings.forEach { runCatching { it.resume() } }
        return true
    }

    private fun recordingFor(back: Boolean): Recording? = if (back) backRecording else frontRecording

    /** Pausar bara en kamera – den andra fortsätter spela in i sin fil. */
    fun pauseCamera(back: Boolean): Boolean {
        val rec = recordingFor(back) ?: return false
        return runCatching { rec.pause() }.isSuccess
    }

    fun resumeCamera(back: Boolean): Boolean {
        val rec = recordingFor(back) ?: return false
        return runCatching { rec.resume() }.isSuccess
    }

    /** Avslutar en kameras fil; den andra kameran fortsätter i samma take. */
    fun stopCamera(back: Boolean): Boolean {
        val rec = recordingFor(back) ?: return false
        val ok = runCatching { rec.stop() }.isSuccess
        if (back) backRecording = null else frontRecording = null
        return ok
    }

    /** Tryck-för-fokus och exponering. [nx]/[ny] är 0..1 i förhandsvisningen. */
    fun focusAt(isFront: Boolean, nx: Float, ny: Float, view: PreviewView?) {
        if (view == null) return
        val camera = (if (isFront) frontCamera else backCamera) ?: return
        runCatching {
            val point = view.meteringPointFactory.createPoint(
                nx.coerceIn(0f, 1f) * view.width,
                ny.coerceIn(0f, 1f) * view.height
            )
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
            camera.cameraControl.startFocusAndMetering(action)
        }
    }

    /** Stoppar alla inspelningar; [then] körs när alla filer är färdigskrivna. */
    fun stopRecording(then: (() -> Unit)? = null) {
        if (activeRecordings.isEmpty()) { then?.invoke(); return }
        then?.let { stopWaiters += it }
        activeRecordings.toList().forEach { runCatching { it.stop() } }
    }

    private fun onFinalized(rec: Recording?) {
        activeRecordings.remove(rec)
        if (activeRecordings.isEmpty()) {
            val waiting = stopWaiters.toList()
            stopWaiters.clear()
            waiting.forEach { it() }
        }
    }

    private fun VideoCapture<Recorder>.record(
        ctx: Context, name: String, audio: Boolean, executor: java.util.concurrent.Executor
    ): Recording {
        val pending = output.prepareRecording(ctx, mediaStoreOutput(ctx, name))
        if (audio) pending.withAudioEnabled()
        val ref = arrayOfNulls<Recording>(1)
        val rec = pending.start(executor) { ev -> if (ev is VideoRecordEvent.Finalize) onFinalized(ref[0]) }
        ref[0] = rec
        return rec
    }

    private fun hdSelector(): QualitySelector =
        QualitySelector.fromOrderedList(
            listOf(Quality.HD, Quality.SD),
            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
        )

    private fun qualitySelector(q: QualityOption): QualitySelector {
        val fallback = FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
        return when (q) {
            QualityOption.HD720 -> QualitySelector.fromOrderedList(listOf(Quality.HD, Quality.SD), fallback)
            QualityOption.FHD1080 -> QualitySelector.fromOrderedList(listOf(Quality.FHD, Quality.HD, Quality.SD), fallback)
            QualityOption.UHD4K -> QualitySelector.fromOrderedList(listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD), fallback)
        }
    }

    private fun mediaStoreOutput(context: Context, displayName: String): MediaStoreOutputOptions {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$displayName.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/DualShots")
        }
        return MediaStoreOutputOptions.Builder(
            context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()
    }
}
