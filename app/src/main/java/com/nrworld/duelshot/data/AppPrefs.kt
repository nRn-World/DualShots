package com.nrworld.duelshot.data

import android.content.Context
import androidx.camera.core.AspectRatio
import com.nrworld.duelshot.model.AspectOption
import com.nrworld.duelshot.model.CodecOption
import com.nrworld.duelshot.model.PipStyle
import com.nrworld.duelshot.model.QualityOption
import com.nrworld.duelshot.model.RecordingMode

/**
 * Enkel SharedPreferences-lagring för användarinställningar.
 * (DataStore kan bytas in sen; prefs räcker för en testbar APK.)
 */
class AppPrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("duelshot_prefs", Context.MODE_PRIVATE)

    /** Läser en enum säkert: en okänd eller gammal sparad text ger standardvärdet i stället för krasch. */
    private inline fun <reified T : Enum<T>> read(key: String, fallback: T): T {
        val name = sp.getString(key, null) ?: return fallback
        return runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
    }

    init {
        // Dual View är standard: det är läget med den lilla kameran.
        // En gång per installation flyttas det gamla förvalet (Separate) över.
        if (!sp.getBoolean(KEY_SETUP_DONE, false)) {
            val stored = sp.getString(KEY_MODE, null)
            sp.edit().apply {
                if (stored == null || stored == RecordingMode.SEPARATE.name) {
                    putString(KEY_MODE, RecordingMode.DUAL_VIEW.name)
                }
                putBoolean(KEY_SETUP_DONE, true)
            }.apply()
        }
    }

    var mode: RecordingMode
        get() = read(KEY_MODE, RecordingMode.DUAL_VIEW)
        set(v) = sp.edit().putString(KEY_MODE, v.name).apply()

    var frontAspect: AspectOption
        get() = read(KEY_FRONT, AspectOption.R_9_16)
        set(v) = sp.edit().putString(KEY_FRONT, v.name).apply()

    var backAspect: AspectOption
        get() = read(KEY_BACK, AspectOption.R_16_9)
        set(v) = sp.edit().putString(KEY_BACK, v.name).apply()

    var quality: QualityOption
        get() = read(KEY_QUALITY, QualityOption.FHD1080)
        set(v) = sp.edit().putString(KEY_QUALITY, v.name).apply()

    var codec: CodecOption
        get() = read(KEY_CODEC, CodecOption.MP4)
        set(v) = sp.edit().putString(KEY_CODEC, v.name).apply()

    /** Format på Dual View-filen; null = följer bakkameran (Auto). */
    var saveAspect: AspectOption?
        get() = sp.getString(KEY_SAVE_ASPECT, null)?.let { n -> AspectOption.values().firstOrNull { it.name == n } }
        set(v) = sp.edit().putString(KEY_SAVE_ASPECT, v?.name).apply()

    var grid: Boolean
        get() = sp.getBoolean(KEY_GRID, true)
        set(v) = sp.edit().putBoolean(KEY_GRID, v).apply()

    var recordAudio: Boolean
        get() = sp.getBoolean(KEY_AUDIO, true)
        set(v) = sp.edit().putBoolean(KEY_AUDIO, v).apply()

    /** Nedräkning innan inspelning startar (0, 3 eller 5 sekunder). */
    var countdownSec: Int
        get() = sp.getInt(KEY_COUNTDOWN, 0).let { if (it == 3 || it == 5) it else 0 }
        set(v) = sp.edit().putInt(KEY_COUNTDOWN, if (v == 3 || v == 5) v else 0).apply()

    /** Dual View-layout: PiP, Circle eller 50/50 Split. */
    var pipStyle: PipStyle
        get() = read(KEY_PIP_STYLE, PipStyle.PIP)
        set(v) = sp.edit().putString(KEY_PIP_STYLE, v.name).apply()

    /** Favoritmarkerade klipp/foton (nycklar som "v123" / "p456"). */
    var favorites: Set<String>
        get() = sp.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_FAVORITES, v.toSet()).apply()

    /** Visas onboarding bara första gången. */
    var onboarded: Boolean
        get() = sp.getBoolean(KEY_ONBOARDED, false)
        set(v) = sp.edit().putBoolean(KEY_ONBOARDED, v).apply()

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_FRONT = "front_aspect"
        private const val KEY_BACK = "back_aspect"
        private const val KEY_QUALITY = "quality"
        private const val KEY_CODEC = "codec"
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_SETUP_DONE = "setup_done"
        private const val KEY_SAVE_ASPECT = "save_aspect"
        private const val KEY_GRID = "grid"
        private const val KEY_AUDIO = "record_audio"
        private const val KEY_COUNTDOWN = "countdown_sec"
        private const val KEY_PIP_STYLE = "pip_style"
        private const val KEY_FAVORITES = "favorites"
    }
}

/** Mappar AspectOption → riktiga CameraX AspectRatio-konstanter. */
fun AspectOption.toCxRatio(): Int = when (this) {
    AspectOption.R_9_16, AspectOption.R_16_9 -> AspectRatio.RATIO_16_9
    AspectOption.R_1_1, AspectOption.R_4_3 -> AspectRatio.RATIO_4_3
}
