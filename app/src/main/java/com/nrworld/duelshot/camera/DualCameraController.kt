package com.nrworld.duelshot.camera

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
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
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Environment

/**
 * Bild-i-bild-placering för Dual View. [cx] och [cy] är mitten av den lilla
 * rutan som andel av bildytan (0..1), [scale] är dess bredd som andel av ytan,
 * och [swapCameras] gör frontkameran stor och bakkameran liten.
 */
data class PipLayout(
    val cx: Float = 0.68f,
    val cy: Float = 0.34f,
    val scale: Float = 0.34f,
    val swapCameras: Boolean = false,
    /** false = lilla kameran avstängd; bara stora kameran syns i inspelningen. */
    val visible: Boolean = true,
    /** true = lilla kameran pausad; dess ruta döljs i inspelningen. */
    val paused: Boolean = false
)

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

    // En Recording per kamera så att en kamera kan pausas eller klickas bort
    // medan den andra fortsätter spela in.
    private var backRecording: Recording? = null
    private var frontRecording: Recording? = null
    private var composedRecording: Recording? = null

    /** Beskärning per kamera via ViewPort; stängs av om enheten inte klarar den. */
    private var cropEnabled = true

    // OverlayEffect i Dual View-gruppen gjorde den stora kameran svart och den lilla
    // rutan felplacerad på riktiga telefoner, så paus döljer kameran (alpha 0) i stället.
    /** Stora kameran pausad i Dual View: döljs vid nästa bindning. */
    var mainPaused = false

    /** Ljud i inspelningen (inställningen Record Audio). */
    var recordAudio = true

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

    /** Senaste felet från en misslyckad sammansättning, för statusraden. */
    var lastBindError: String? = null
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
     * [backView]/[frontView] behövs för Separate/Both. Dual View visar den
     * sammansatta strömmen i [composedView] och behöver inga egna previews.
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
        saveAspect: AspectOption? = null
    ): String {
        val p = provider ?: return "Camera is not ready yet."

        p.unbindAll()
        backVideo = null; frontVideo = null; composedVideo = null
        backCamera = null; frontCamera = null
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
                RecordingMode.DUAL_VIEW -> bindComposed(
                    p, bs, fs, frontAspect, backAspect, qs, lifecycleOwner,
                    backView, frontView, composedView ?: backView, pip, saveAspect
                )
                RecordingMode.BOTH -> bindBoth(
                    p, bs, fs, frontAspect, backAspect, qs, lifecycleOwner,
                    backView, frontView, composedView, pip
                )
            }
            activeMode = wanted
            "Camera ready – ${wanted.title}."
        } catch (e: Exception) {
            if (cropEnabled) {
                cropEnabled = false
                return bind(wanted, frontAspect, backAspect, quality, lifecycleOwner, backView, frontView, composedView, pip, saveAspect)
            }
            // Fallback: rapportera att läget inte stöds och låt skärmen binda om i
            // Separate med de ytor som faktiskt är kvar. Att binda här skulle binda
            // till ytor som kastas när layouten byts – det gav svarta rutor.
            // Orsaken tas med i statusraden så att den syns utan logcat.
            runCatching { p.unbindAll() }
            activeMode = RecordingMode.SEPARATE
            val why = (e.localizedMessage ?: e.javaClass.simpleName).take(140)
            lastBindError = why
            "Composing the two cameras failed ($why) – recording as Separate files instead."
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
            rememberCameras(listOf(p.bindToLifecycle(owner, sel, groupOf(aspect, prev, vc))))
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
        frontAspect: AspectOption, backAspect: AspectOption, qs: QualitySelector,
        owner: LifecycleOwner, backView: PreviewView?, frontView: PreviewView?,
        composedView: PreviewView?, pip: PipLayout, saveAspect: AspectOption?
    ) {
        val composedPreview = previewFor(composedView)
        val composed = VideoCapture.withOutput(Recorder.Builder().setQualitySelector(qs).build())
        composedVideo = composed
        // Sammansättningen tillåter bara en preview + en VideoCapture i gruppen.
        // Egna previews per kamera här fick bindningen att kasta och då blev
        // Dual View aldrig en enda fil – det var regressionen.
        val builder = UseCaseGroup.Builder()
            .addUseCase(composedPreview).addUseCase(composed)
        // Valt filformat (t.ex. 16:9 för YouTube) beskär den sammansatta bilden.
        if (saveAspect != null) viewPortFor(saveAspect)?.let { builder.setViewPort(it) }
        val group = builder.build()
        val largeSel = if (pip.swapCameras) fs else bs
        val smallSel = if (pip.swapCameras) bs else fs
        val largeAspect = if (pip.swapCameras) frontAspect else backAspect
        rememberCameras(
            p.bindToLifecycle(
                listOf(
                    ConcurrentCamera.SingleCameraConfig(largeSel, group, fullComposition(largeAspect, saveAspect), owner),
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

    /**
     * Stora kameran fyller filen. Med ett eget filformat behåller kameran sitt
     * format (t.ex. 9:16 i en 16:9-fil) och resten blir svarta kanter.
     * En pausad stor kamera döljs med alpha 0 (gäller från nästa bindning/take).
     */
    private fun fullComposition(large: AspectOption? = null, out: AspectOption? = null): CompositionSettings {
        var sx = 1f
        var sy = 1f
        if (large != null && out != null && cropEnabled) {
            val rl = large.ratio()
            val ro = out.ratio()
            if (rl < ro) sx = rl / ro else if (rl > ro) sy = ro / rl
        }
        val alpha = if (mainPaused) 0f else 1f
        return CompositionSettings.Builder().setAlpha(alpha).setOffset(0f, 0f).setScale(sx, sy).build()
    }

    private fun AspectOption.ratio(): Float = when (this) {
        AspectOption.R_9_16 -> 9f / 16f
        AspectOption.R_16_9 -> 16f / 9f
        AspectOption.R_1_1 -> 1f
        AspectOption.R_4_3 -> 4f / 3f
    }

    /**
     * CameraX-offset är normaliserad (-1..1, 0 = mitten) och får max vara 1 - skala,
     * annars hamnar den lilla rutan utanför bilden.
     *
     * Skalan är andel av bildens bredd och höjden följer den lilla kamerans format,
     * så en porträttkamera i en liggande inspelning blir högre än en "scale"-ruta.
     *
     * NDC har positiv Y uppåt medan skärmen har Y nedåt. X räknas därför som
     * (2·cx − 1) men Y måste vändas: (1 − 2·cy), annars hamnar rutan spegelvänd
     * vertikalt jämfört med ramen på skärmen.
     */
    private fun insetComposition(pip: PipLayout): CompositionSettings {
        val scale = pip.scale.coerceIn(0.18f, 0.6f)
        val limit = 1f - scale
        val ox = (2f * pip.cx - 1f).coerceIn(-limit, limit)
        val oy = (1f - 2f * pip.cy).coerceIn(-limit, limit)
        // Avstängd eller pausad liten kamera: alpha 0 så att stora kameran fyller rutan.
        val alpha = if (pip.visible && !pip.paused) 1f else 0f
        return CompositionSettings.Builder().setAlpha(alpha).setOffset(ox, oy).setScale(scale, scale).build()
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
        backRecording = null; frontRecording = null; composedRecording = null
        try {
            when (activeMode) {
                RecordingMode.SEPARATE -> {
                    if (singleFallback) {
                        val isBack = backVideo != null
                        val v = backVideo ?: frontVideo
                        val n = if (isBack) "dualshot_back_$stamp" else "dualshot_front_$stamp"
                        v?.let {
                            val rec = it.record(context, n, audio = recordAudio, executor)
                            started += rec
                            if (isBack) backRecording = rec else frontRecording = rec
                            names += "$n.mp4"
                        }
                    } else {
                        backVideo?.let {
                            val rec = it.record(context, "dualshot_back_$stamp", audio = recordAudio, executor)
                            started += rec; backRecording = rec; names += "dualshot_back_$stamp.mp4"
                        }
                        frontVideo?.let {
                            val rec = it.record(context, "dualshot_front_$stamp", audio = false, executor)
                            started += rec; frontRecording = rec; names += "dualshot_front_$stamp.mp4"
                        }
                    }
                }
                RecordingMode.DUAL_VIEW -> {
                    composedVideo?.let {
                        val rec = it.record(context, "dualshot_combined_$stamp", audio = recordAudio, executor)
                        started += rec; composedRecording = rec; names += "dualshot_combined_$stamp.mp4"
                    }
                }
                RecordingMode.BOTH -> {
                    composedVideo?.let {
                        val rec = it.record(context, "dualshot_combined_$stamp", audio = recordAudio, executor)
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
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/DualShots")
        }
        return MediaStoreOutputOptions.Builder(
            context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()
    }
}
