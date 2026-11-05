package com.nrworld.duelshot.camera

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Rational
import android.view.Surface
import android.view.TextureView
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CompositionSettings
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
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
 * Bild-i-bild-placering för Dual View.
 */
data class PipLayout(
    val cx: Float = 0.74f,
    val cy: Float = 0.24f,
    val scale: Float = 0.34f,
    val scaleY: Float = 0.34f,
    val swapCameras: Boolean = false,
    val visible: Boolean = true,
    val paused: Boolean = false
)

/**
 * Styr dubbla kameror (fram + bak) och inspelning i alla tre lägen:
 * - Dual View använder [DualGlComposer] för laggfri 60 fps flytt/skalning av lilla kameran
 *   i realtid (även mitt under inspelning) till EN sammansatt MP4-fil.
 * - Separate Videos spelar in två separata MP4-filer.
 * - Both Options spelar in sammansatt + separata filer (med robust fallback).
 */
class DualCameraController(private val context: Context) {

    val glComposer = DualGlComposer(context)

    private var provider: ProcessCameraProvider? = null
    private var backSelector: CameraSelector? = null
    private var frontSelector: CameraSelector? = null

    private var backVideo: VideoCapture<Recorder>? = null
    private var frontVideo: VideoCapture<Recorder>? = null
    private var composedVideo: VideoCapture<Recorder>? = null

    private val activeRecordings = mutableListOf<Recording>()
    private val stopWaiters = mutableListOf<() -> Unit>()

    private var backRecording: Recording? = null
    private var frontRecording: Recording? = null
    private var composedRecording: Recording? = null
    private var glRecordingActive = false

    private var cropEnabled = true

    private var backCamera: Camera? = null
    private var frontCamera: Camera? = null
    private var backHwZoom = 1f
    private var frontHwZoom = 1f

    /** Sätter hårdvaruzoom på bak-/frontkameran och returnerar vad hårdvaran faktiskt accepterade. */
    fun setZoom(back: Float? = null, front: Float? = null): Pair<Float, Float> {
        if (back != null) backHwZoom = applyZoom(backCamera, back)
        if (front != null) frontHwZoom = applyZoom(frontCamera, front)
        return backHwZoom to frontHwZoom
    }

    private fun applyZoom(camera: Camera?, ratio: Float): Float {
        if (camera == null) return 1f
        val state = camera.cameraInfo.zoomState.value ?: return 1f
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
        backHwZoom = applyZoom(backCamera, backHwZoom)
        frontHwZoom = applyZoom(frontCamera, frontHwZoom)
    }

    var activeMode = RecordingMode.DUAL_VIEW
        private set
    var supported = false
        private set
    var canBind = false
        private set
    var singleFallback = false
        private set
    var singleUseFront = false
        private set

    val canFlipSingle: Boolean
        get() = backSelector != null && frontSelector != null

    fun flipSingleCamera(): Boolean {
        if (!canFlipSingle) return singleUseFront
        singleUseFront = !singleUseFront
        return singleUseFront
    }

    fun attachDualTextureView(view: TextureView) {
        glComposer.attachTextureView(view)
    }

    fun updateDualScene(scene: DualGlComposer.SceneState) {
        glComposer.updateScene(scene)
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

    fun bind(
        wanted: RecordingMode,
        frontAspect: AspectOption,
        backAspect: AspectOption,
        quality: QualityOption,
        lifecycleOwner: LifecycleOwner,
        backView: PreviewView?,
        frontView: PreviewView?,
        composedView: PreviewView?,
        pip: PipLayout = PipLayout()
    ): String {
        val p = provider ?: return "Starting cameras…"

        p.unbindAll()
        backVideo = null; frontVideo = null; composedVideo = null
        backCamera = null; frontCamera = null
        singleFallback = false

        val qs = qualitySelector(quality)

        if (!supported || backSelector == null || frontSelector == null) {
            return bindSingle(p, frontAspect, backAspect, qs, lifecycleOwner, backView, frontView)
        }
        val bs = backSelector!!
        val fs = frontSelector!!

        return when (wanted) {
            RecordingMode.DUAL_VIEW -> {
                try {
                    bindGlDual(p, bs, fs, lifecycleOwner)
                    activeMode = RecordingMode.DUAL_VIEW
                    "Dual View · front + back"
                } catch (e: Exception) {
                    runCatching { p.unbindAll() }
                    bindSingle(p, frontAspect, backAspect, qs, lifecycleOwner, backView, frontView)
                }
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

    /**
     * Binder endast EN Preview per kamera direkt in i [DualGlComposer]:
     * ger 60 fps realtids-PiP på skärmen OCH sammansatt inspelning i en enda MP4-fil
     * utan någon ombindning när rutan flyttas, skalas, byter plats eller pausas.
     */
    private fun bindGlDual(
        p: ProcessCameraProvider,
        bs: CameraSelector,
        fs: CameraSelector,
        owner: LifecycleOwner
    ) {
        val backPrev = Preview.Builder()
            .setTargetRotation(Surface.ROTATION_0)
            .build()
            .also { it.setSurfaceProvider(glComposer.glExecutor, glComposer.backSurfaceProvider) }

        val frontPrev = Preview.Builder()
            .setTargetRotation(Surface.ROTATION_0)
            .build()
            .also { it.setSurfaceProvider(glComposer.glExecutor, glComposer.frontSurfaceProvider) }

        val backGroup = UseCaseGroup.Builder().addUseCase(backPrev).build()
        val frontGroup = UseCaseGroup.Builder().addUseCase(frontPrev).build()

        rememberCameras(
            p.bindToLifecycle(
                listOf(
                    ConcurrentCamera.SingleCameraConfig(bs, backGroup, owner),
                    ConcurrentCamera.SingleCameraConfig(fs, frontGroup, owner)
                )
            ).cameras
        )
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
        quality: QualityOption = QualityOption.FHD1080,
        audioEnabled: Boolean = true,
        onStarted: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(context)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + (if (part > 1) "_p$part" else "")

        if (activeMode == RecordingMode.DUAL_VIEW && !singleFallback) {
            glComposer.startRecording(
                baseName = "dualshot_combined_$stamp",
                quality = quality,
                audioEnabled = audioEnabled,
                onStarted = { fileName ->
                    glRecordingActive = true
                    onStarted(listOf(fileName))
                },
                onError = onError
            )
            return
        }

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
                    } else {
                        backVideo?.let {
                            val rec = it.record(context, "dualshot_back_$stamp", audio = audioEnabled, executor)
                            started += rec; backRecording = rec; names += "dualshot_back_$stamp.mp4"
                        }
                        frontVideo?.let {
                            val rec = it.record(context, "dualshot_front_$stamp", audio = false, executor)
                            started += rec; frontRecording = rec; names += "dualshot_front_$stamp.mp4"
                        }
                    }
                }
                RecordingMode.DUAL_VIEW -> Unit
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

    fun pauseRecording(): Boolean {
        if (glRecordingActive) return glComposer.pauseRecording()
        if (activeRecordings.isEmpty()) return false
        activeRecordings.forEach { runCatching { it.pause() } }
        return true
    }

    fun resumeRecording(): Boolean {
        if (glRecordingActive) return glComposer.resumeRecording()
        if (activeRecordings.isEmpty()) return false
        activeRecordings.forEach { runCatching { it.resume() } }
        return true
    }

    private fun recordingFor(back: Boolean): Recording? = if (back) backRecording else frontRecording

    fun pauseCamera(back: Boolean): Boolean {
        val rec = recordingFor(back) ?: return false
        return runCatching { rec.pause() }.isSuccess
    }

    fun resumeCamera(back: Boolean): Boolean {
        val rec = recordingFor(back) ?: return false
        return runCatching { rec.resume() }.isSuccess
    }

    fun stopCamera(back: Boolean): Boolean {
        val rec = recordingFor(back) ?: return false
        val ok = runCatching { rec.stop() }.isSuccess
        if (back) backRecording = null else frontRecording = null
        return ok
    }

    fun focusAt(isFront: Boolean, nx: Float, ny: Float, view: PreviewView? = null) {
        val camera = (if (isFront) frontCamera else backCamera) ?: return
        runCatching {
            val point = if (view != null && view.width > 0 && view.height > 0) {
                view.meteringPointFactory.createPoint(
                    nx.coerceIn(0f, 1f) * view.width,
                    ny.coerceIn(0f, 1f) * view.height
                )
            } else {
                SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(
                    nx.coerceIn(0f, 1f),
                    ny.coerceIn(0f, 1f)
                )
            }
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
            camera.cameraControl.startFocusAndMetering(action)
        }
    }

    fun stopRecording(then: (() -> Unit)? = null) {
        if (glRecordingActive) {
            glRecordingActive = false
            glComposer.stopRecording(then)
            return
        }
        if (activeRecordings.isEmpty()) { then?.invoke(); return }
        then?.let { stopWaiters += it }
        activeRecordings.toList().forEach { runCatching { it.stop() } }
    }

    fun release() {
        runCatching { provider?.unbindAll() }
        glComposer.release()
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
