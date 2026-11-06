package com.nrworld.duelshot.camera

import android.content.ContentValues
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer

/**
 * Kodar den ihopritade Dual View-bilden (från [GlCompositor]) och ljudet till en mp4.
 * Ett paus-läge hoppar över både bild och ljud, och tidsstämplarna fortsätter där de
 * slutade så att filen blir en sammanhängande video.
 */
class DualRecorder(
    context: Context,
    val outW: Int,
    val outH: Int,
    bitrate: Int,
    private val withAudio: Boolean
) {
    private val file = File(context.cacheDir, "dual_${System.currentTimeMillis()}.mp4")
    private val videoEnc: MediaCodec
    val inputSurface: Surface

    private var audioEnc: MediaCodec? = null
    private var audioRecord: AudioRecord? = null
    private val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private var videoTrack = -1
    private var audioTrack = -1
    private var muxerStarted = false
    private val muxLock = Any()
    private val pending = ArrayList<Triple<Int, ByteArray, MediaCodec.BufferInfo>>()

    private val startNs = System.nanoTime()
    @Volatile var paused = false
        private set
    private var pausedAtNs = 0L
    private var pausedTotalNs = 0L

    @Volatile private var stopping = false
    private var videoThread: Thread? = null
    private var audioThread: Thread? = null

    init {
        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, outW, outH).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        videoEnc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        videoEnc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = videoEnc.createInputSurface()
        videoEnc.start()

        if (withAudio) {
            runCatching {
                val rate = 44100
                val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val rec = AudioRecord(
                    MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, min * 4
                )
                check(rec.state == AudioRecord.STATE_INITIALIZED)
                val afmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
                }
                val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                enc.configure(afmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                enc.start()
                rec.startRecording()
                audioRecord = rec
                audioEnc = enc
            }
        }
        videoThread = Thread({ drain(videoEnc, isVideo = true) }, "dual-video").also { it.start() }
        audioEnc?.let { enc -> audioThread = Thread({ audioLoop(enc) }, "dual-audio").also { it.start() } }
    }

    /** Tidsstämpel (ns) för nästa bildruta, utan tiden som varit pausad. */
    fun ptsNs(): Long = System.nanoTime() - startNs - pausedTotalNs

    fun pause() {
        if (paused) return
        pausedAtNs = System.nanoTime()
        paused = true
    }

    fun resume() {
        if (!paused) return
        pausedTotalNs += System.nanoTime() - pausedAtNs
        paused = false
    }

    /** Avslutar, skriver filen och lägger den i Movies/DualShots. [done] får namnet om det gick. */
    fun stop(context: Context, displayName: String, done: (Boolean) -> Unit) {
        Thread({
            stopping = true
            runCatching { videoEnc.signalEndOfInputStream() }
            runCatching { videoThread?.join(4000) }
            runCatching { audioThread?.join(4000) }
            runCatching { audioRecord?.stop() }
            runCatching { audioRecord?.release() }
            val ok = synchronized(muxLock) {
                val started = muxerStarted
                runCatching { if (started) muxer.stop() }
                runCatching { muxer.release() }
                started
            }
            runCatching { videoEnc.stop() }
            runCatching { videoEnc.release() }
            runCatching { audioEnc?.stop() }
            runCatching { audioEnc?.release() }
            runCatching { inputSurface.release() }
            val saved = ok && saveToGallery(context, displayName)
            file.delete()
            done(saved)
        }, "dual-stop").start()
    }

    // ----------------------------------------------------------------- kodning

    private fun audioLoop(enc: MediaCodec) {
        val rec = audioRecord ?: return
        val buf = ByteArray(4096)
        val info = MediaCodec.BufferInfo()
        var eosSent = false
        var ended = false
        var guard = 0
        while (!ended && guard < 400) {
            if (!eosSent) {
                val n = rec.read(buf, 0, buf.size)
                val inIdx = enc.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val inBuf = enc.getInputBuffer(inIdx)!!
                    inBuf.clear()
                    if (stopping) {
                        enc.queueInputBuffer(inIdx, 0, 0, ptsNs() / 1000L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        eosSent = true
                    } else if (n > 0 && !paused) {
                        inBuf.put(buf, 0, n)
                        val ptsUs = (ptsNs() - n / 2L * 1_000_000_000L / 44100L) / 1000L
                        enc.queueInputBuffer(inIdx, 0, n, ptsUs.coerceAtLeast(0), 0)
                    } else {
                        enc.queueInputBuffer(inIdx, 0, 0, 0, 0)
                    }
                }
            } else {
                guard++
                Thread.sleep(5)
            }
            if (drainOnce(enc, info, isVideo = false)) ended = true
        }
    }

    private fun drain(enc: MediaCodec, isVideo: Boolean) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            if (drainOnce(enc, info, isVideo)) return
        }
    }

    /** Hämtar en utbuffert. Returnerar true när strömmen är slut. */
    private fun drainOnce(enc: MediaCodec, info: MediaCodec.BufferInfo, isVideo: Boolean): Boolean {
        val idx = try {
            enc.dequeueOutputBuffer(info, if (isVideo) 10_000 else 0)
        } catch (_: Exception) {
            return true
        }
        when {
            idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> synchronized(muxLock) {
                val track = muxer.addTrack(enc.outputFormat)
                if (isVideo) videoTrack = track else audioTrack = track
                val ready = videoTrack >= 0 && (audioEnc == null || audioTrack >= 0)
                if (ready && !muxerStarted) {
                    muxer.start()
                    muxerStarted = true
                    pending.forEach { (t, data, i) -> muxer.writeSampleData(t, ByteBuffer.wrap(data), i) }
                    pending.clear()
                }
            }
            idx >= 0 -> {
                val out = enc.getOutputBuffer(idx)
                val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                if (out != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                    out.position(info.offset)
                    out.limit(info.offset + info.size)
                    synchronized(muxLock) {
                        val track = if (isVideo) videoTrack else audioTrack
                        if (muxerStarted) {
                            muxer.writeSampleData(track, out, info)
                        } else if (track >= 0) {
                            val bytes = ByteArray(info.size)
                            out.get(bytes)
                            pending += Triple(track, bytes, MediaCodec.BufferInfo().also { it.set(0, info.size, info.presentationTimeUs, info.flags) })
                        }
                    }
                }
                enc.releaseOutputBuffer(idx, false)
                if (eos) return true
            }
        }
        return false
    }

    // ----------------------------------------------------------------- spara

    private fun saveToGallery(context: Context, displayName: String): Boolean = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$displayName.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/DualShots")
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "DualShots")
                dir.mkdirs()
                put(MediaStore.MediaColumns.DATA, File(dir, "$displayName.mp4").absolutePath)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        val copied = resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) }; true } ?: false
        if (!copied) resolver.delete(uri, null, null)
        copied
    }.getOrDefault(false)
}
