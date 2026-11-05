package com.nrworld.duelshot.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.Surface
import android.view.TextureView
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import com.nrworld.duelshot.model.QualityOption
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Realtids OpenGL ES 2.0-kompositör för Dual View:
 * - Tar emot fram- och bakkamerans strömmar som OES-texturer från CameraX Preview
 * - Ritar bakgrundskamera + rundad, flyttbar, skalbar bild-i-bild-kamera i 60 fps till skärmens TextureView
 * - Ritar exakt samma bild till MediaRecorder-ytan så att hela tagningen sparas i EN sammansatt MP4-fil,
 *   och så att flytt, storleksändring, swap, zoom, paus och stängning av lilla kameran fungerar live även under inspelning.
 */
class DualGlComposer(private val context: Context) {

    data class SceneState(
        val pipLeftNorm: Float = 0.57f,
        val pipTopNorm: Float = 0.08f,
        val pipWidthNorm: Float = 0.34f,
        val pipHeightNorm: Float = 0.34f,
        val pipCxNorm: Float = 0.74f,
        val pipCyNorm: Float = 0.24f,
        val pipCornerNorm: Float = 0.045f,
        val smallAspectRatio: Float = 9f / 16f,
        val swapCameras: Boolean = false,
        val pipVisible: Boolean = true,
        val pipPaused: Boolean = false,
        val backZoom: Float = 1f,
        val frontZoom: Float = 1f
    )

    private class CameraStream(
        val texId: Int,
        val surfaceTexture: SurfaceTexture,
        val surface: Surface,
        var bufW: Int,
        var bufH: Int,
        var rotationDeg: Int = 90,
        var hasFrame: Boolean = false
    ) {
        val texMatrix = FloatArray(16)
    }

    @Volatile
    private var scene = SceneState()

    private val released = AtomicBoolean(false)
    private val drawScheduled = AtomicBoolean(false)

    private val glThread = HandlerThread("DualShotsGlComposer").apply { start() }
    private val glHandler = Handler(glThread.looper)
    val glExecutor: Executor = Executor { cmd ->
        if (!released.get()) {
            glHandler.post(cmd)
        }
    }

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var pbufferSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var program = 0
    private var aPositionLoc = 0
    private var aUvLoc = 0
    private var uTexMatrixLoc = 0
    private var uCropLoc = 0
    private var uIsPipLoc = 0
    private var uPausedLoc = 0
    private var uDrawBorderLoc = 0
    private var uPipSizeLoc = 0
    private var uRadiusLoc = 0
    private var uHasTexLoc = 0

    private val quadBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(4 * 4 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            // x, y, u, v
            put(
                floatArrayOf(
                    -1f, -1f, 0f, 0f,
                    1f, -1f, 1f, 0f,
                    -1f, 1f, 0f, 1f,
                    1f, 1f, 1f, 1f
                )
            )
            position(0)
        }

    private var backStream: CameraStream? = null
    private var frontStream: CameraStream? = null

    private var displaySurface: Surface? = null
    private var displayEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var displayW = 0
    private var displayH = 0

    // Inspelning till EN sammansatt MP4-fil
    private var mediaRecorder: MediaRecorder? = null
    private var recorderSurface: Surface? = null
    private var recorderEglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var recW = 1080
    private var recH = 1920
    private var pendingUri: Uri? = null
    private var pendingPfd: ParcelFileDescriptor? = null
    private var legacyFile: File? = null
    private var recordingActive = false
    private var recordingPaused = false
    private var pauseStartedNs = 0L
    private var totalPausedNs = 0L
    private var lastRecorderDrawNs = 0L

    init {
        glHandler.post { initGl() }
    }

    val backSurfaceProvider = Preview.SurfaceProvider { request ->
        bindCameraRequest(request, isFront = false)
    }

    val frontSurfaceProvider = Preview.SurfaceProvider { request ->
        bindCameraRequest(request, isFront = true)
    }

    fun updateScene(next: SceneState) {
        scene = next
        scheduleDraw()
    }

    fun attachTextureView(view: TextureView) {
        view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                glHandler.post {
                    if (released.get()) return@post
                    destroyDisplaySurface()
                    displaySurface = Surface(st)
                    displayEglSurface = createWindowSurface(displaySurface!!)
                    displayW = width
                    displayH = height
                    drawNow()
                }
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                glHandler.post {
                    displayW = width
                    displayH = height
                    drawNow()
                }
            }

            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                val latch = CountDownLatch(1)
                glHandler.post {
                    destroyDisplaySurface()
                    latch.countDown()
                }
                runCatching { latch.await(400, TimeUnit.MILLISECONDS) }
                return true
            }

            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
        }

        val existing = view.surfaceTexture
        if (view.isAvailable && existing != null) {
            glHandler.post {
                if (released.get()) return@post
                destroyDisplaySurface()
                displaySurface = Surface(existing)
                displayEglSurface = createWindowSurface(displaySurface!!)
                displayW = view.width
                displayH = view.height
                drawNow()
            }
        }
    }

    fun startRecording(
        baseName: String,
        quality: QualityOption,
        audioEnabled: Boolean,
        onStarted: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val mainHandler = Handler(context.mainLooper)
        glHandler.post {
            if (released.get() || recordingActive) return@post
            try {
                val candidates = when (quality) {
                    QualityOption.UHD4K -> listOf(Triple(1440, 2560, 16_000_000), Triple(1080, 1920, 10_000_000), Triple(720, 1280, 5_000_000))
                    QualityOption.FHD1080 -> listOf(Triple(1080, 1920, 10_000_000), Triple(720, 1280, 5_000_000))
                    QualityOption.HD720 -> listOf(Triple(720, 1280, 5_000_000))
                }

                var startedOk = false
                var lastErr: Exception? = null
                for ((w, h, bitrate) in candidates) {
                    try {
                        prepareAndStartRecorder(baseName, w, h, bitrate, audioEnabled)
                        startedOk = true
                        break
                    } catch (e: Exception) {
                        lastErr = e
                        cleanupFailedRecorder()
                    }
                }
                if (!startedOk) {
                    throw (lastErr ?: IllegalStateException("Encoder unavailable"))
                }

                recordingActive = true
                recordingPaused = false
                pauseStartedNs = 0L
                totalPausedNs = 0L
                lastRecorderDrawNs = 0L
                drawNow()
                mainHandler.post { onStarted("$baseName.mp4") }
            } catch (e: Exception) {
                cleanupFailedRecorder()
                mainHandler.post { onError("Recording did not start: ${e.localizedMessage ?: "encoder error"}") }
            }
        }
    }

    fun pauseRecording(): Boolean {
        if (!recordingActive || recordingPaused) return false
        val latch = CountDownLatch(1)
        var ok = false
        glHandler.post {
            ok = runCatching {
                mediaRecorder?.pause()
                recordingPaused = true
                pauseStartedNs = System.nanoTime()
                true
            }.getOrDefault(false)
            latch.countDown()
        }
        runCatching { latch.await(500, TimeUnit.MILLISECONDS) }
        return ok
    }

    fun resumeRecording(): Boolean {
        if (!recordingActive || !recordingPaused) return false
        val latch = CountDownLatch(1)
        var ok = false
        glHandler.post {
            ok = runCatching {
                mediaRecorder?.resume()
                if (pauseStartedNs > 0L) {
                    totalPausedNs += (System.nanoTime() - pauseStartedNs).coerceAtLeast(0L)
                }
                pauseStartedNs = 0L
                recordingPaused = false
                true
            }.getOrDefault(false)
            latch.countDown()
        }
        runCatching { latch.await(500, TimeUnit.MILLISECONDS) }
        return ok
    }

    fun stopRecording(onStopped: (() -> Unit)? = null) {
        val mainHandler = Handler(context.mainLooper)
        glHandler.post {
            finishRecorderInternal()
            onStopped?.let { mainHandler.post(it) }
        }
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        val latch = CountDownLatch(1)
        glHandler.post {
            finishRecorderInternal()
            destroyDisplaySurface()
            backStream = null
            frontStream = null
            if (program != 0) {
                GLES20.glDeleteProgram(program)
                program = 0
            }
            if (pbufferSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, pbufferSurface)
                pbufferSurface = EGL14.EGL_NO_SURFACE
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglTerminate(eglDisplay)
                eglDisplay = EGL14.EGL_NO_DISPLAY
            }
            latch.countDown()
        }
        runCatching { latch.await(600, TimeUnit.MILLISECONDS) }
        glThread.quitSafely()
    }

    private fun prepareAndStartRecorder(
        baseName: String,
        w: Int,
        h: Int,
        bitrate: Int,
        audioEnabled: Boolean
    ) {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$baseName.mp4")
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/DualShots")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create MediaStore entry")
            pendingUri = uri
            pendingPfd = resolver.openFileDescriptor(uri, "rw")
                ?: throw IllegalStateException("Could not open video output")
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "DualShots"
            ).apply { mkdirs() }
            legacyFile = File(dir, "$baseName.mp4")
        }

        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        if (audioEnabled) {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
        }
        mr.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        val pfd = pendingPfd
        if (pfd != null) {
            mr.setOutputFile(pfd.fileDescriptor)
        } else {
            mr.setOutputFile(legacyFile!!.absolutePath)
        }
        mr.setVideoEncodingBitRate(bitrate)
        mr.setVideoFrameRate(30)
        mr.setVideoSize(w, h)
        mr.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        if (audioEnabled) {
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(128_000)
            mr.setAudioSamplingRate(44_100)
        }
        mr.prepare()
        val surf = mr.surface
        val eglSurf = createWindowSurface(surf)
        if (eglSurf == EGL14.EGL_NO_SURFACE) {
            surf.release()
            mr.release()
            throw IllegalStateException("Could not create EGL encoder surface")
        }
        mr.start()

        mediaRecorder = mr
        recorderSurface = surf
        recorderEglSurface = eglSurf
        recW = w
        recH = h
    }

    private fun cleanupFailedRecorder() {
        if (recorderEglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(eglDisplay, recorderEglSurface)
            recorderEglSurface = EGL14.EGL_NO_SURFACE
        }
        runCatching { recorderSurface?.release() }
        recorderSurface = null
        runCatching { mediaRecorder?.reset() }
        runCatching { mediaRecorder?.release() }
        mediaRecorder = null
        runCatching { pendingPfd?.close() }
        pendingPfd = null
        pendingUri?.let { uri -> runCatching { context.contentResolver.delete(uri, null, null) } }
        pendingUri = null
        legacyFile?.let { f -> runCatching { f.delete() } }
        legacyFile = null
    }

    private fun finishRecorderInternal() {
        if (!recordingActive && mediaRecorder == null) return
        recordingActive = false
        recordingPaused = false

        if (recorderEglSurface != EGL14.EGL_NO_SURFACE) {
            makePbufferCurrent()
            EGL14.eglDestroySurface(eglDisplay, recorderEglSurface)
            recorderEglSurface = EGL14.EGL_NO_SURFACE
        }
        val mr = mediaRecorder
        mediaRecorder = null
        val stopOk = runCatching { mr?.stop() }.isSuccess
        runCatching { mr?.reset() }
        runCatching { mr?.release() }
        runCatching { recorderSurface?.release() }
        recorderSurface = null

        runCatching { pendingPfd?.close() }
        pendingPfd = null

        val uri = pendingUri
        pendingUri = null
        if (uri != null) {
            if (stopOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                runCatching { context.contentResolver.update(uri, done, null, null) }
            } else if (!stopOk) {
                runCatching { context.contentResolver.delete(uri, null, null) }
            }
        }

        val file = legacyFile
        legacyFile = null
        if (file != null && stopOk) {
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("video/mp4"), null)
        }
    }

    private fun bindCameraRequest(request: SurfaceRequest, isFront: Boolean) {
        if (released.get()) {
            request.willNotProvideSurface()
            return
        }
        makePbufferCurrent()
        val texId = createOesTexture()
        val st = SurfaceTexture(texId).apply {
            setDefaultBufferSize(request.resolution.width, request.resolution.height)
        }
        val surface = Surface(st)
        val stream = CameraStream(
            texId = texId,
            surfaceTexture = st,
            surface = surface,
            bufW = request.resolution.width,
            bufH = request.resolution.height,
            rotationDeg = if (isFront) 270 else 90
        )

        request.setTransformationInfoListener(glExecutor) { info ->
            stream.rotationDeg = info.rotationDegrees
            scheduleDraw()
        }

        st.setOnFrameAvailableListener({
            if (!released.get()) {
                runCatching {
                    makePbufferCurrent()
                    st.updateTexImage()
                    st.getTransformMatrix(stream.texMatrix)
                    stream.hasFrame = true
                }
                scheduleDraw()
            }
        }, glHandler)

        if (isFront) frontStream = stream else backStream = stream

        request.provideSurface(surface, glExecutor) {
            if (isFront && frontStream === stream) frontStream = null
            if (!isFront && backStream === stream) backStream = null
            st.setOnFrameAvailableListener(null)
            runCatching { surface.release() }
            runCatching { st.release() }
            if (eglDisplay != EGL14.EGL_NO_DISPLAY && !released.get()) {
                makePbufferCurrent()
                GLES20.glDeleteTextures(1, intArrayOf(texId), 0)
            }
        }
    }

    private fun scheduleDraw() {
        if (released.get()) return
        if (drawScheduled.compareAndSet(false, true)) {
            glHandler.post {
                drawScheduled.set(false)
                drawNow()
            }
        }
    }

    private fun drawNow() {
        if (released.get() || eglDisplay == EGL14.EGL_NO_DISPLAY || program == 0) return
        val s = scene

        if (displayEglSurface != EGL14.EGL_NO_SURFACE && displayW > 0 && displayH > 0) {
            if (EGL14.eglMakeCurrent(eglDisplay, displayEglSurface, displayEglSurface, eglContext)) {
                renderScene(dstW = displayW, dstH = displayH, s = s, forRecorder = false)
                EGL14.eglSwapBuffers(eglDisplay, displayEglSurface)
            }
        }

        if (recordingActive && !recordingPaused && recorderEglSurface != EGL14.EGL_NO_SURFACE) {
            val nowNs = System.nanoTime()
            // Begränsa till ~33 fps till kodaren även om användaren drar i 120 Hz på skärmen.
            if (nowNs - lastRecorderDrawNs >= 28_000_000L) {
                lastRecorderDrawNs = nowNs
                if (EGL14.eglMakeCurrent(eglDisplay, recorderEglSurface, recorderEglSurface, eglContext)) {
                    renderScene(dstW = recW, dstH = recH, s = s, forRecorder = true)
                    val ptsNs = (nowNs - totalPausedNs).coerceAtLeast(0L)
                    EGLExt.eglPresentationTimeANDROID(eglDisplay, recorderEglSurface, ptsNs)
                    EGL14.eglSwapBuffers(eglDisplay, recorderEglSurface)
                }
            }
        }
    }

    private fun renderScene(dstW: Int, dstH: Int, s: SceneState, forRecorder: Boolean) {
        GLES20.glViewport(0, 0, dstW, dstH)
        GLES20.glClearColor(0.02f, 0.024f, 0.04f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val largeStream = if (s.swapCameras) frontStream else backStream
        val smallStream = if (s.swapCameras) backStream else frontStream
        val largeZoom = if (s.swapCameras) s.frontZoom else s.backZoom
        val smallZoom = if (s.swapCameras) s.backZoom else s.frontZoom

        // 1. Stor bakgrundskamera i full yta
        drawQuad(
            stream = largeStream,
            boxW = dstW,
            boxH = dstH,
            zoom = largeZoom,
            isPip = false,
            paused = false,
            drawBorder = false,
            cornerRadiusPx = 0f
        )

        // 2. Liten bild-i-bild-kamera med rundade hörn på exakt samma koordinater som ramen
        if (s.pipVisible) {
            val pw: Int
            val ph: Int
            val px: Int
            val pyTop: Int
            if (!forRecorder) {
                pw = (s.pipWidthNorm * dstW).roundToInt().coerceAtLeast(2)
                ph = (s.pipHeightNorm * dstH).roundToInt().coerceAtLeast(2)
                px = (s.pipLeftNorm * dstW).roundToInt()
                pyTop = (s.pipTopNorm * dstH).roundToInt()
            } else {
                val ratio = s.smallAspectRatio.coerceAtLeast(0.1f)
                var rw = dstW * s.pipWidthNorm
                var rh = rw / ratio
                val maxH = dstH * 0.72f
                val maxW = dstW * 0.56f
                if (rh > maxH) { rh = maxH; rw = rh * ratio }
                if (rw > maxW) { rw = maxW; rh = rw / ratio }
                pw = rw.roundToInt().coerceAtLeast(2)
                ph = rh.roundToInt().coerceAtLeast(2)
                val mx = (dstW * 0.035f).roundToInt()
                val my = (dstH * 0.035f).roundToInt()
                px = (s.pipCxNorm * dstW - pw / 2f).roundToInt().coerceIn(mx, (dstW - pw - mx).coerceAtLeast(mx))
                pyTop = (s.pipCyNorm * dstH - ph / 2f).roundToInt().coerceIn(my, (dstH - ph - my).coerceAtLeast(my))
            }

            val glY = dstH - pyTop - ph
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            GLES20.glViewport(px, glY, pw, ph)

            val radiusPx = (s.pipCornerNorm * dstW).coerceIn(2f, min(pw, ph) * 0.48f)
            drawQuad(
                stream = smallStream,
                boxW = pw,
                boxH = ph,
                zoom = smallZoom,
                isPip = true,
                paused = s.pipPaused,
                drawBorder = forRecorder,
                cornerRadiusPx = radiusPx
            )
            GLES20.glDisable(GLES20.GL_BLEND)
        }
    }

    private fun drawQuad(
        stream: CameraStream?,
        boxW: Int,
        boxH: Int,
        zoom: Float,
        isPip: Boolean,
        paused: Boolean,
        drawBorder: Boolean,
        cornerRadiusPx: Float
    ) {
        GLES20.glUseProgram(program)

        val hasValidFrame = stream != null && stream.hasFrame
        val z = zoom.coerceIn(1f, 3f)
        var cropX = 1f / z
        var cropY = 1f / z

        if (stream != null && stream.bufW > 0 && stream.bufH > 0 && boxW > 0 && boxH > 0) {
            val rotated = (stream.rotationDeg % 180) != 0
            val srcW = if (rotated) stream.bufH else stream.bufW
            val srcH = if (rotated) stream.bufW else stream.bufH
            val srcAspect = srcW.toFloat() / srcH.toFloat()
            val dstAspect = boxW.toFloat() / boxH.toFloat()
            if (dstAspect > srcAspect) {
                cropX = 1f / z
                cropY = (srcAspect / dstAspect) / z
            } else {
                cropX = (dstAspect / srcAspect) / z
                cropY = 1f / z
            }
        }

        GLES20.glUniform2f(uCropLoc, cropX, cropY)
        GLES20.glUniform1i(uIsPipLoc, if (isPip) 1 else 0)
        GLES20.glUniform1i(uPausedLoc, if (paused) 1 else 0)
        GLES20.glUniform1i(uDrawBorderLoc, if (drawBorder) 1 else 0)
        GLES20.glUniform2f(uPipSizeLoc, boxW.toFloat(), boxH.toFloat())
        GLES20.glUniform1f(uRadiusLoc, cornerRadiusPx)
        GLES20.glUniform1i(uHasTexLoc, if (hasValidFrame) 1 else 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        if (hasValidFrame && stream != null) {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, stream.texId)
            GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, stream.texMatrix, 0)
        } else {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
            GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, IDENTITY_MATRIX, 0)
        }

        quadBuffer.position(0)
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, 16, quadBuffer)

        quadBuffer.position(2)
        GLES20.glEnableVertexAttribArray(aUvLoc)
        GLES20.glVertexAttribPointer(aUvLoc, 2, GLES20.GL_FLOAT, false, 16, quadBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aUvLoc)
    }

    private fun initGl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
        val cfg = configs[0] ?: return
        eglConfig = cfg

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, cfg, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)

        val pbufAttribs = intArrayOf(EGL14.EGL_WIDTH, 4, EGL14.EGL_HEIGHT, 4, EGL14.EGL_NONE)
        pbufferSurface = EGL14.eglCreatePbufferSurface(eglDisplay, cfg, pbufAttribs, 0)
        makePbufferCurrent()

        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aUvLoc = GLES20.glGetAttribLocation(program, "aUv")
        uTexMatrixLoc = GLES20.glGetUniformLocation(program, "uTexMatrix")
        uCropLoc = GLES20.glGetUniformLocation(program, "uCrop")
        uIsPipLoc = GLES20.glGetUniformLocation(program, "uIsPip")
        uPausedLoc = GLES20.glGetUniformLocation(program, "uPaused")
        uDrawBorderLoc = GLES20.glGetUniformLocation(program, "uDrawBorder")
        uPipSizeLoc = GLES20.glGetUniformLocation(program, "uPipSize")
        uRadiusLoc = GLES20.glGetUniformLocation(program, "uRadius")
        uHasTexLoc = GLES20.glGetUniformLocation(program, "uHasTex")
        val sTexLoc = GLES20.glGetUniformLocation(program, "sTexture")
        GLES20.glUseProgram(program)
        GLES20.glUniform1i(sTexLoc, 0)
    }

    private fun makePbufferCurrent() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY && pbufferSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(eglDisplay, pbufferSurface, pbufferSurface, eglContext)
        }
    }

    private fun createWindowSurface(surface: Surface): EGLSurface {
        val cfg = eglConfig ?: return EGL14.EGL_NO_SURFACE
        return runCatching {
            EGL14.eglCreateWindowSurface(eglDisplay, cfg, surface, intArrayOf(EGL14.EGL_NONE), 0)
        }.getOrDefault(EGL14.EGL_NO_SURFACE)
    }

    private fun destroyDisplaySurface() {
        if (displayEglSurface != EGL14.EGL_NO_SURFACE && eglDisplay != EGL14.EGL_NO_DISPLAY) {
            makePbufferCurrent()
            EGL14.eglDestroySurface(eglDisplay, displayEglSurface)
            displayEglSurface = EGL14.EGL_NO_SURFACE
        }
        runCatching { displaySurface?.release() }
        displaySurface = null
        displayW = 0
        displayH = 0
    }

    private fun createOesTexture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        val id = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, id)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return id
    }

    private fun buildProgram(vsSrc: String, fsSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vsSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsSrc)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val sh = GLES20.glCreateShader(type)
        GLES20.glShaderSource(sh, src)
        GLES20.glCompileShader(sh)
        return sh
    }

    companion object {
        private const val EGL_RECORDABLE_ANDROID = 0x3142

        private val IDENTITY_MATRIX = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )

        private const val VERTEX_SHADER = """
            uniform mat4 uTexMatrix;
            uniform vec2 uCrop;
            attribute vec4 aPosition;
            attribute vec2 aUv;
            varying vec2 vTexCoord;
            varying vec2 vQuadUv;
            void main() {
                gl_Position = aPosition;
                vQuadUv = aUv;
                vec2 cropped = vec2(0.5, 0.5) + (aUv - vec2(0.5, 0.5)) * uCrop;
                vTexCoord = (uTexMatrix * vec4(cropped, 0.0, 1.0)).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            varying vec2 vQuadUv;
            uniform samplerExternalOES sTexture;
            uniform int uIsPip;
            uniform int uPaused;
            uniform int uDrawBorder;
            uniform int uHasTex;
            uniform vec2 uPipSize;
            uniform float uRadius;

            void main() {
                vec4 cam = (uHasTex == 1)
                    ? texture2D(sTexture, vTexCoord)
                    : vec4(0.03, 0.04, 0.06, 1.0);

                if (uPaused == 1) {
                    float lum = dot(cam.rgb, vec3(0.299, 0.587, 0.114));
                    cam.rgb = mix(vec3(lum) * 0.25, vec3(0.03, 0.04, 0.06), 0.72);
                }

                if (uIsPip == 0) {
                    gl_FragColor = vec4(cam.rgb, 1.0);
                    return;
                }

                vec2 p = vQuadUv * uPipSize;
                vec2 halfSize = uPipSize * 0.5;
                vec2 q = abs(p - halfSize) - (halfSize - vec2(uRadius));
                float dist = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - uRadius;
                float alpha = 1.0 - smoothstep(-0.9, 0.9, dist);
                if (alpha <= 0.005) discard;

                vec3 rgb = cam.rgb;
                if (uDrawBorder == 1) {
                    float borderW = max(2.0, uPipSize.x * 0.008);
                    float borderMask = smoothstep(-borderW - 1.0, -borderW + 0.5, dist);
                    rgb = mix(rgb, vec3(1.0, 1.0, 1.0), borderMask * 0.55);
                }

                gl_FragColor = vec4(rgb, alpha);
            }
        """
    }
}
