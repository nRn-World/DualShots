package com.nrworld.duelshot.camera

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.CompositionSettings
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import android.util.Rational
import android.view.Surface
import androidx.camera.video.VideoRecordEvent
import androidx.camera.core.AspectRatio
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.nrworld.duelshot.model.AspectOption
import com.nrworld.duelshot.model.QualityOption
import com.nrworld.duelshot.model.RecordingMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Environment

/**
 * Styr dubbla kameror (fram + bak) och inspelning i tre lägen.
 * Alltid robust: om ett avancerat läge (composition) inte stöds av enheten
 * faller vi tillbaka till "Separate" så inspelning alltid fungerar.
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

    /** Beskärning per kamera via ViewPort; stängs av om enheten inte klarar den. */
    private var cropEnabled = true

    /** Vilket läge som faktiskt band (kan skilja sig från önskat pga fallback). */
    var activeMode = RecordingMode.SEPARATE
        private set
    var supported = false
        private set

    /** Minst en kamera går att använda (dubbel eller enskild). */
    var canBind = false
        private set

    /** Sant när dubbla kameror inte stöds och vi föll tillbaka till en kamera. */
    var singleFallback = false
        private set

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
                supported -> "Ready – dual cameras."
                canBind -> "This device does not support simultaneous dual cameras – single camera enabled."
                else -> "No camera available on this device."
            }
        } catch (e: Exception) {
            "Could not start camera: ${e.localizedMessage}"
        }
    }

    private fun pick(p: ProcessCameraProvider, sel: CameraSelector): CameraSelector? =
        runCatching { if (p.hasCamera(sel)) sel else null }.getOrNull()

    /**
     * Binder kamerorna enligt [wanted]. returnerar statusmeddelande.
     * [backView]/[frontView] behövs för Separate/Both, [composedView] för Dual View.
     */
    fun bind(
        wanted: RecordingMode,
        frontAspect: AspectOption,
        backAspect: AspectOption,
        quality: QualityOption,
        lifecycleOwner: LifecycleOwner,
        backView: PreviewView?,
        frontView: PreviewView?,
        composedView: PreviewView?
    ): String {
        val p = provider ?: return "Camera is not ready yet."

        p.unbindAll()
        backVideo = null; frontVideo = null; composedVideo = null
        singleFallback = false

        val qs = qualitySelector(quality)

        // Dubbla kameror kräver båda selectorerna; annars enskild kamera som fallback.
        if (!supported || backSelector == null || frontSelector == null) {
            return bindSingle(p, frontAspect, backAspect, qs, lifecycleOwner, backView, frontView)
        }
        val bs = backSelector!!
        val fs = frontSelector!!

        return try {
            when (wanted) {
                RecordingMode.SEPARATE -> bindSeparate(p, bs, fs, frontAspect, backAspect, qs, lifecycleOwner, backView, frontView)
                RecordingMode.DUAL_VIEW -> bindComposed(p, bs, fs, frontAspect, backAspect, qs, lifecycleOwner, composedView ?: backView)
                RecordingMode.BOTH -> bindBoth(p, bs, fs, frontAspect, backAspect, qs, lifecycleOwner, backView, frontView, composedView)
            }
            activeMode = wanted
            "Camera ready – ${wanted.title}."
        } catch (e: Exception) {
            if (cropEnabled) {
                cropEnabled = false
                return bind(wanted, frontAspect, backAspect, quality, lifecycleOwner, backView, frontView, composedView)
            }
            // Fallback: försök enklare läge så användaren alltid kan spela in.
            try {
                p.unbindAll()
                bindSeparate(p, bs, fs, frontAspect, backAspect, qs, lifecycleOwner, backView, frontView)
                activeMode = RecordingMode.SEPARATE
                "Advanced mode not supported here – recording as Separate files instead."
            } catch (e2: Exception) {
                "Could not bind cameras: ${e2.localizedMessage}"
            }
        }
    }

    private fun bindSingle(
        p: ProcessCameraProvider,
        frontAspect: AspectOption, backAspect: AspectOption, qs: QualitySelector,
        owner: LifecycleOwner, backView: PreviewView?, frontView: PreviewView?
    ): String {
        val useFront = backSelector == null && frontSelector != null
        val sel = (if (useFront) frontSelector else backSelector)
            ?: return "No camera available."
        return try {
            p.unbindAll()
            val view = if (useFront) frontView else backView
            val aspect = if (useFront) frontAspect else backAspect
            val (prev, vc) = previewAndCapture(view, aspect, qs)
            // Enskild kamera binds via vanliga (icke-koncurrenta) bindToLifecycle.
            p.bindToLifecycle(owner, sel, groupOf(aspect, prev, vc))
            if (useFront) frontVideo = vc else backVideo = vc
            activeMode = RecordingMode.SEPARATE
            singleFallback = true
            "Single camera (${if (useFront) "FRONT" else "BACK"}) – recording one file."
        } catch (e: Exception) {
            if (cropEnabled) {
                cropEnabled = false
                bindSingle(p, frontAspect, backAspect, qs, owner, backView, frontView)
            } else "Could not bind camera: ${e.localizedMessage}"
        }
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
        p.bindToLifecycle(
            listOf(
                ConcurrentCamera.SingleCameraConfig(bs, backGroup, owner),
                ConcurrentCamera.SingleCameraConfig(fs, frontGroup, owner)
            )
        )
    }

    private fun bindComposed(
        p: ProcessCameraProvider, bs: CameraSelector, fs: CameraSelector,
        frontAspect: AspectOption, backAspect: AspectOption, qs: QualitySelector,
        owner: LifecycleOwner, composedView: PreviewView?
    ) {
        val preview = Preview.Builder().build().apply {
            composedView?.let { setSurfaceProvider(it.surfaceProvider) }
        }
        val composed = VideoCapture.withOutput(Recorder.Builder().setQualitySelector(qs).build())
        composedVideo = composed
        val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(composed).build()
        // Back fyller hela bilden, front blir bild-i-bild uppe till höger.
        val backCfg = ConcurrentCamera.SingleCameraConfig(
            bs, group,
            CompositionSettings.Builder().setAlpha(1f).setOffset(0f, 0f).setScale(1f, 1f).build(),
            owner
        )
        val frontCfg = ConcurrentCamera.SingleCameraConfig(
            fs, group,
            CompositionSettings.Builder().setAlpha(1f).setOffset(0.33f, -0.33f).setScale(0.34f, 0.34f).build(),
            owner
        )
        p.bindToLifecycle(listOf(backCfg, frontCfg))
    }

    private fun bindBoth(
        p: ProcessCameraProvider, bs: CameraSelector, fs: CameraSelector,
        frontAspect: AspectOption, backAspect: AspectOption, qs: QualitySelector,
        owner: LifecycleOwner, backView: PreviewView?, frontView: PreviewView?, composedView: PreviewView?
    ) {
        // Delad composition-output (en fil) + egna VideoCapture per kamera (två filer).
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

        p.bindToLifecycle(
            listOf(
                ConcurrentCamera.SingleCameraConfig(bs, backGroup,
                    CompositionSettings.Builder().setAlpha(1f).setOffset(0f, 0f).setScale(1f, 1f).build(), owner),
                ConcurrentCamera.SingleCameraConfig(fs, frontGroup,
                    CompositionSettings.Builder().setAlpha(1f).setOffset(0.33f, -0.33f).setScale(0.34f, 0.34f).build(), owner)
            )
        )
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

    private fun previewAndCapture(
        view: PreviewView?, aspect: AspectOption, qs: QualitySelector
    ): Pair<Preview, VideoCapture<Recorder>> {
        val preview = Preview.Builder().build().apply {
            view?.let { setSurfaceProvider(it.surfaceProvider) }
        }
        // OBS: I concurrent (dubbel-kamera) läge får man INTE sätta setTargetAspectRatio –
        // CameraX kastar då "setTargetAspectRatio is not supported". Upplösning styrs av
        // QualitySelector + enhetens samtidiga ström-konfiguration.
        val video = VideoCapture.Builder<Recorder>(
            Recorder.Builder().setQualitySelector(qs).build()
        ).build()
        return preview to video
    }

    fun startRecording(part: Int = 1, onStarted: (List<String>) -> Unit, onError: (String) -> Unit) {
        val executor = ContextCompat.getMainExecutor(context)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + (if (part > 1) "_p$part" else "")
        val started = mutableListOf<Recording>()
        val names = mutableListOf<String>()
        try {
            when (activeMode) {
                RecordingMode.SEPARATE -> {
                    if (singleFallback) {
                        val isBack = backVideo != null
                        val v = backVideo ?: frontVideo
                        val n = if (isBack) "duelshot_back_$stamp" else "duelshot_front_$stamp"
                        v?.let { started += it.record(context, n, audio = true, executor); names += "$n.mp4" }
                    } else {
                        backVideo?.let { started += it.record(context, "duelshot_back_$stamp", audio = true, executor); names += "duelshot_back_$stamp.mp4" }
                        frontVideo?.let { started += it.record(context, "duelshot_front_$stamp", audio = false, executor); names += "duelshot_front_$stamp.mp4" }
                    }
                }
                RecordingMode.DUAL_VIEW -> {
                    composedVideo?.let { started += it.record(context, "duelshot_combined_$stamp", audio = true, executor); names += "duelshot_combined_$stamp.mp4" }
                }
                RecordingMode.BOTH -> {
                    composedVideo?.let { started += it.record(context, "duelshot_combined_$stamp", audio = true, executor); names += "duelshot_combined_$stamp.mp4" }
                    backVideo?.let { started += it.record(context, "duelshot_back_$stamp", audio = false, executor); names += "duelshot_back_$stamp.mp4" }
                    frontVideo?.let { started += it.record(context, "duelshot_front_$stamp", audio = false, executor); names += "duelshot_front_$stamp.mp4" }
                }
            }
            activeRecordings += started
            onStarted(names)
        } catch (e: Exception) {
            started.forEach { runCatching { it.stop() } }
            onError("Recording did not start: ${e.localizedMessage}")
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

    private fun qualitySelector(q: QualityOption): QualitySelector = when (q) {
        QualityOption.HD720 -> QualitySelector.fromOrderedList(listOf(Quality.HD, Quality.SD))
        QualityOption.FHD1080 -> QualitySelector.fromOrderedList(listOf(Quality.FHD, Quality.HD, Quality.SD))
        QualityOption.UHD4K -> QualitySelector.fromOrderedList(listOf(Quality.UHD, Quality.FHD, Quality.HD))
    }

    private fun AspectOption.toCx(): Int = when (this) {
        AspectOption.R_9_16, AspectOption.R_16_9 -> AspectRatio.RATIO_16_9
        AspectOption.R_1_1, AspectOption.R_4_3 -> AspectRatio.RATIO_4_3
    }

    private fun mediaStoreOutput(context: Context, displayName: String): MediaStoreOutputOptions {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$displayName.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/DuelShots")
        }
        return MediaStoreOutputOptions.Builder(
            context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()
    }
}
