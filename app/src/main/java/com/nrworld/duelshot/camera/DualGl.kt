package com.nrworld.duelshot.camera

import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.camera.core.SurfaceRequest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Hur Dual View ritas ihop. Allt är bråkdelar av bilden så att förhandsvisning och
 * inspelad fil blir identiska, oavsett upplösning.
 */
data class GlLayout(
    val swap: Boolean = false,
    val smallVisible: Boolean = true,
    val smallPaused: Boolean = false,
    val mainPaused: Boolean = false,
    val cx: Float = 0.68f,
    val cy: Float = 0.34f,
    val scale: Float = 0.34f,
    /** Bredd/höjd för stora kamerans valda format. */
    val largeRatio: Float = 16f / 9f,
    /** Bredd/höjd för lilla kamerans valda format (t.ex. 9:16 = stående ruta). */
    val smallRatio: Float = 9f / 16f
)

/**
 * Egen OpenGL-sammansättning av bak- och frontkamera, som webbversionens canvas:
 * stora kameran fyller bilden och lilla kameran ligger som en rund-hörnad ruta i
 * sitt eget format. CameraX egen sammansättning kan inte göra en stående ruta
 * i en liggande bild utan att förvränga, därför ritas bilden här i stället.
 *
 * Ritar både till förhandsvisningen (TextureView) och, under inspelning, till
 * kodarens yta. All GL-kod körs på en egen tråd.
 */
class GlCompositor {

    private class Cam {
        var tex = 0
        var st: SurfaceTexture? = null
        var surface: Surface? = null
        var w = 640
        var h = 480
        @Volatile var rotation = 0
        @Volatile var fresh = false
        var hasFrame = false
    }

    private val thread = HandlerThread("dual-gl").apply { start() }
    private val handler = Handler(thread.looper)

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null
    private var pbuffer: EGLSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private val cams = arrayOf(Cam(), Cam())
    private var uvBuffer: FloatBuffer? = null

    private var previewSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var encoderSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    @Volatile var layout = GlLayout()

    /** Testläge utan två kameror: frontens ruta visar bakkameran (speglad). */
    @Volatile var simulateFront = false

    /** Aktiv inspelning; när den är satt och inte pausad matas även kodaren. */
    @Volatile var recorder: DualRecorder? = null

    @Volatile private var released = false

    // Uniform-lägen
    private var uRect = 0
    private var uM0 = 0
    private var uM1 = 0
    private var uSize = 0
    private var uRadius = 0
    private var uBorder = 0
    private var uSolid = 0
    private var uRound = 0
    private var aUv = 0

    init {
        onGl { initGl() }
        handler.post(object : Runnable {
            override fun run() {
                if (released) return
                runCatching { renderTick() }
                handler.postDelayed(this, 33)
            }
        })
    }

    // ------------------------------------------------------------ gränssnitt

    /** Ger CameraX en yta för kamera [index] (0 = bak, 1 = fram). */
    fun provide(index: Int, request: SurfaceRequest, executor: Executor) {
        val res = request.resolution
        val surface = onGl {
            val cam = cams[index]
            cam.w = res.width
            cam.h = res.height
            cam.hasFrame = false
            cam.st?.release()
            cam.surface?.release()
            val st = SurfaceTexture(cam.tex)
            st.setDefaultBufferSize(res.width, res.height)
            st.setOnFrameAvailableListener({ cam.fresh = true }, handler)
            cam.st = st
            Surface(st).also { cam.surface = it }
        }
        if (surface == null) {
            request.willNotProvideSurface()
            return
        }
        request.provideSurface(surface, executor) { }
    }

    /** Medurs rotation (0/90/180/270) som gör kamerans buffert upprätt i förhandsvisningen. */
    fun setRotation(index: Int, degrees: Int) {
        cams[index].rotation = degrees
    }

    fun setPreview(texture: SurfaceTexture?) {
        onGl {
            if (previewSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
                EGL14.eglDestroySurface(display, previewSurface)
                previewSurface = EGL14.EGL_NO_SURFACE
            }
            if (texture != null) {
                previewSurface = EGL14.eglCreateWindowSurface(display, config, texture, intArrayOf(EGL14.EGL_NONE), 0)
            }
        }
    }

    fun setEncoder(surface: Surface?) {
        onGl {
            if (encoderSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
                EGL14.eglDestroySurface(display, encoderSurface)
                encoderSurface = EGL14.EGL_NO_SURFACE
            }
            if (surface != null) {
                encoderSurface = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
            }
        }
    }

    fun release() {
        if (released) return
        released = true
        onGl {
            runCatching {
                EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
                if (previewSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, previewSurface)
                if (encoderSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, encoderSurface)
                cams.forEach {
                    it.st?.release(); it.surface?.release()
                    if (it.tex != 0) GLES20.glDeleteTextures(1, intArrayOf(it.tex), 0)
                }
                if (program != 0) GLES20.glDeleteProgram(program)
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(display, pbuffer)
                EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
            }
            Unit
        }
        thread.quitSafely()
    }

    // ------------------------------------------------------------------ tråd

    private fun <T> onGl(block: () -> T): T? {
        if (Thread.currentThread() === thread) return block()
        var result: T? = null
        val latch = CountDownLatch(1)
        handler.post {
            result = runCatching(block).getOrNull()
            latch.countDown()
        }
        latch.await(4, TimeUnit.SECONDS)
        return result
    }

    private fun initGl() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val v = IntArray(2)
        EGL14.eglInitialize(display, v, 0, v, 1)
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            0x3142, 1, // EGL_RECORDABLE_ANDROID
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0)
        config = configs[0]
        context = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        pbuffer = EGL14.eglCreatePbufferSurface(
            display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
        )
        EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)

        program = buildProgram(VERTEX, FRAGMENT)
        uRect = GLES20.glGetUniformLocation(program, "uRect")
        uM0 = GLES20.glGetUniformLocation(program, "uM0")
        uM1 = GLES20.glGetUniformLocation(program, "uM1")
        uSize = GLES20.glGetUniformLocation(program, "uSize")
        uRadius = GLES20.glGetUniformLocation(program, "uRadius")
        uBorder = GLES20.glGetUniformLocation(program, "uBorder")
        uSolid = GLES20.glGetUniformLocation(program, "uSolid")
        uRound = GLES20.glGetUniformLocation(program, "uRound")
        aUv = GLES20.glGetAttribLocation(program, "aUv")

        uvBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)); position(0)
        }
        cams.forEach { cam ->
            val t = IntArray(1)
            GLES20.glGenTextures(1, t, 0)
            cam.tex = t[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cam.tex)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }
    }

    private fun buildProgram(vs: String, fs: String): Int {
        fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { GLES20.glGetShaderInfoLog(s) }
            return s
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        return p
    }

    // --------------------------------------------------------------- ritning

    private fun renderTick() {
        // Hämta nya kamerabilder.
        cams.forEachIndexed { i, cam ->
            if (i == 1 && simulateFront) return@forEachIndexed
            val st = cam.st ?: return@forEachIndexed
            if (cam.fresh) {
                cam.fresh = false
                EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
                runCatching {
                    st.updateTexImage()
                    cam.hasFrame = true
                }
            }
        }
        if (!cams[0].hasFrame) return

        if (previewSurface != EGL14.EGL_NO_SURFACE) {
            drawTo(previewSurface, encoder = false)
        }
        val rec = recorder
        if (rec != null && encoderSurface != EGL14.EGL_NO_SURFACE && !rec.paused) {
            val pts = rec.ptsNs()
            EGLExt.eglPresentationTimeANDROID(display, encoderSurface, pts)
            drawTo(encoderSurface, encoder = true)
        }
        EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
    }

    private fun drawTo(surface: EGLSurface, encoder: Boolean) {
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return
        val wh = IntArray(1)
        EGL14.eglQuerySurface(display, surface, EGL14.EGL_WIDTH, wh, 0)
        val w = wh[0]
        EGL14.eglQuerySurface(display, surface, EGL14.EGL_HEIGHT, wh, 0)
        val h = wh[0]
        if (w <= 0 || h <= 0) return
        drawScene(w.toFloat(), h.toFloat())
        EGL14.eglSwapBuffers(display, surface)
    }

    private fun drawScene(w: Float, h: Float) {
        val l = layout
        GLES20.glViewport(0, 0, w.toInt(), h.toInt())
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(program)

        val largeIdx = if (l.swap) 1 else 0
        val smallIdx = 1 - largeIdx

        // Stora kameran fyller alltid hela bilden – samma utsnitt som webbens canvas.
        // Kamerans eget format beskärs i stället för att ge svarta kanter.
        val large = RectF(0f, 0f, w, h)
        if (!l.mainPaused) drawCam(largeIdx, large, w, h, round = false, solid = false)

        if (l.smallVisible) {
            val scale = l.scale.coerceIn(0.18f, 0.6f)
            val pw = w * scale
            val ph = pw / l.smallRatio
            val left = (l.cx * w - pw / 2f).coerceIn(0f, max(0f, w - pw))
            val top = (l.cy * h - ph / 2f).coerceIn(0f, max(0f, h - ph))
            drawCam(smallIdx, RectF(left, top, left + pw, top + ph), w, h, round = true, solid = l.smallPaused)
        }
    }

    private fun drawCam(index: Int, r: RectF, w: Float, h: Float, round: Boolean, solid: Boolean) {
        val texIndex = if (index == 1 && simulateFront) 0 else index
        val cam = cams[texIndex]
        if (!cam.hasFrame && !solid) return

        val dw = r.width()
        val dh = r.height()
        val m = textureMatrix(cam, dw / dh, mirror = index == 1) // frontkameran speglas som en selfie

        GLES20.glUniform4f(uRect, 2f * r.left / w - 1f, 1f - 2f * r.top / h, 2f * r.right / w - 1f, 1f - 2f * r.bottom / h)
        GLES20.glUniform3f(uM0, m[0], m[1], m[2])
        GLES20.glUniform3f(uM1, m[3], m[4], m[5])
        GLES20.glUniform2f(uSize, dw, dh)
        GLES20.glUniform1f(uRadius, if (round) 0.07f * min(dw, dh) else 0f)
        GLES20.glUniform1f(uBorder, max(2f, w / 340f))
        GLES20.glUniform1f(uSolid, if (solid) 1f else 0f)
        GLES20.glUniform1f(uRound, if (round) 1f else 0f)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cam.tex)
        GLES20.glEnableVertexAttribArray(aUv)
        GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 0, uvBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aUv)
    }

    /**
     * Affin avbildning från rutans koordinater (0..1, y nedåt) till kamerabuffertens
     * koordinater (0..1, y nedåt). Tar hänsyn till rotationen som gör bufferten upprätt,
     * självspeglingen och den centrerade fyllningen (samma utsnitt som FILL_CENTER).
     */
    private fun textureMatrix(cam: Cam, quadAspect: Float, mirror: Boolean): FloatArray {
        val rot = ((cam.rotation % 360) + 360) % 360
        val swap = rot == 90 || rot == 270
        val uprightW = (if (swap) cam.h else cam.w).toFloat()
        val uprightH = (if (swap) cam.w else cam.h).toFloat()
        val imgAspect = uprightW / uprightH
        var fx = 1f
        var fy = 1f
        // Cover: bilden ska fylla rutan utan att förvrängas. Är bilden vidare än
        // rutan beskärs sidorna, är den högre beskärs topp/botten. (Samma regel som
        // den gamla inspelningsmotorn använde.)
        if (quadAspect < imgAspect) fx = quadAspect / imgAspect else fy = imgAspect / quadAspect
        var ax = fx
        var cx = 0.5f - 0.5f * fx
        val ay = fy
        val cy = 0.5f - 0.5f * fy
        if (mirror) {
            ax = -ax
            cx = 1f - cx
        }
        // bx = A*sx + B*sy + C, by = D*sx + E*sy + F  (buffertkoordinater, y nedåt)
        return when (rot) {
            90 -> floatArrayOf(0f, ay, cy, -ax, 0f, 1f - cx)
            180 -> floatArrayOf(-ax, 0f, 1f - cx, 0f, -ay, 1f - cy)
            270 -> floatArrayOf(0f, -ay, 1f - cy, ax, 0f, cx)
            else -> floatArrayOf(ax, 0f, cx, 0f, ay, cy)
        }
    }

    private companion object {
        const val VERTEX = """
            attribute vec2 aUv;
            uniform vec4 uRect;
            varying vec2 vUv;
            void main() {
                vUv = aUv;
                gl_Position = vec4(mix(uRect.x, uRect.z, aUv.x), mix(uRect.y, uRect.w, aUv.y), 0.0, 1.0);
            }
        """

        const val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vUv;
            uniform samplerExternalOES uTex;
            uniform vec3 uM0;
            uniform vec3 uM1;
            uniform vec2 uSize;
            uniform float uRadius;
            uniform float uBorder;
            uniform float uSolid;
            uniform float uRound;
            void main() {
                vec3 s = vec3(vUv, 1.0);
                vec2 b = vec2(dot(uM0, s), dot(uM1, s));
                vec2 t = vec2(b.x, 1.0 - b.y);
                vec4 col = uSolid > 0.5 ? vec4(0.0, 0.0, 0.0, 1.0) : texture2D(uTex, t);
                float a = 1.0;
                if (uRound > 0.5) {
                    vec2 pp = (vUv - 0.5) * uSize;
                    vec2 b = uSize * 0.5 - vec2(uRadius);
                    float d = length(max(abs(pp) - b, 0.0)) - uRadius;
                    a = clamp(0.5 - d, 0.0, 1.0);
                    float bd = clamp(d + uBorder + 0.5, 0.0, 1.0);
                    col.rgb = mix(col.rgb, vec3(1.0), 0.55 * bd);
                }
                gl_FragColor = vec4(col.rgb, a);
            }
        """
    }
}
