package com.nrworld.duelshot.data

import android.content.Context
import androidx.camera.core.AspectRatio
import com.nrworld.duelshot.model.AspectOption
import com.nrworld.duelshot.model.CodecOption
import com.nrworld.duelshot.model.QualityOption
import com.nrworld.duelshot.model.RecordingMode

/**
 * Enkel SharedPreferences-lagring för användarinställningar.
 * (DataStore kan bytas in sen; prefs räcker för en testbar APK.)
 */
class AppPrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("duelshot_prefs", Context.MODE_PRIVATE)

    var mode: RecordingMode
        get() = RecordingMode.valueOf(sp.getString(KEY_MODE, RecordingMode.SEPARATE.name)!!)
        set(v) = sp.edit().putString(KEY_MODE, v.name).apply()

    var frontAspect: AspectOption
        get() = AspectOption.valueOf(sp.getString(KEY_FRONT, AspectOption.R_9_16.name)!!)
        set(v) = sp.edit().putString(KEY_FRONT, v.name).apply()

    var backAspect: AspectOption
        get() = AspectOption.valueOf(sp.getString(KEY_BACK, AspectOption.R_16_9.name)!!)
        set(v) = sp.edit().putString(KEY_BACK, v.name).apply()

    var quality: QualityOption
        get() = QualityOption.valueOf(sp.getString(KEY_QUALITY, QualityOption.FHD1080.name)!!)
        set(v) = sp.edit().putString(KEY_QUALITY, v.name).apply()

    var codec: CodecOption
        get() = CodecOption.valueOf(sp.getString(KEY_CODEC, CodecOption.MP4.name)!!)
        set(v) = sp.edit().putString(KEY_CODEC, v.name).apply()

    /** Premium-låst: sätts av köpflödet (här mockad). */
    var isPremium: Boolean
        get() = sp.getBoolean(KEY_PREMIUM, false)
        set(v) = sp.edit().putBoolean(KEY_PREMIUM, v).apply()

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_FRONT = "front_aspect"
        private const val KEY_BACK = "back_aspect"
        private const val KEY_QUALITY = "quality"
        private const val KEY_CODEC = "codec"
        private const val KEY_PREMIUM = "premium"
    }
}

/** Mappar AspectOption → riktiga CameraX AspectRatio-konstanter. */
fun AspectOption.toCxRatio(): Int = when (this) {
    AspectOption.R_9_16, AspectOption.R_16_9 -> AspectRatio.RATIO_16_9
    AspectOption.R_1_1, AspectOption.R_4_3 -> AspectRatio.RATIO_4_3
}
