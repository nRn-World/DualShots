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

    init {
        // Dual View är standard: det är läget med den lilla kameran.
        // En gång per uppdatering säkerställs att äldre installationer som
        // fastnat i Separate öppnar i Dual View precis som webb-previewn.
        if (!sp.getBoolean(KEY_STUDIO_SYNC, false)) {
            sp.edit()
                .putString(KEY_MODE, RecordingMode.DUAL_VIEW.name)
                .putBoolean(KEY_SETUP_DONE, true)
                .putBoolean(KEY_STUDIO_SYNC, true)
                .apply()
        }
    }

    var mode: RecordingMode
        get() = runCatching {
            RecordingMode.valueOf(sp.getString(KEY_MODE, RecordingMode.DUAL_VIEW.name)!!)
        }.getOrDefault(RecordingMode.DUAL_VIEW)
        set(v) = sp.edit().putString(KEY_MODE, v.name).apply()

    var frontAspect: AspectOption
        get() = runCatching {
            AspectOption.valueOf(sp.getString(KEY_FRONT, AspectOption.R_9_16.name)!!)
        }.getOrDefault(AspectOption.R_9_16)
        set(v) = sp.edit().putString(KEY_FRONT, v.name).apply()

    var backAspect: AspectOption
        get() = runCatching {
            AspectOption.valueOf(sp.getString(KEY_BACK, AspectOption.R_16_9.name)!!)
        }.getOrDefault(AspectOption.R_16_9)
        set(v) = sp.edit().putString(KEY_BACK, v.name).apply()

    var quality: QualityOption
        get() = runCatching {
            QualityOption.valueOf(sp.getString(KEY_QUALITY, QualityOption.FHD1080.name)!!)
        }.getOrDefault(QualityOption.FHD1080)
        set(v) = sp.edit().putString(KEY_QUALITY, v.name).apply()

    var codec: CodecOption
        get() = runCatching {
            CodecOption.valueOf(sp.getString(KEY_CODEC, CodecOption.MP4.name)!!)
        }.getOrDefault(CodecOption.MP4)
        set(v) = sp.edit().putString(KEY_CODEC, v.name).apply()

    /** Premium-låst: sätts av köpflödet (här mockad). */
    var isPremium: Boolean
        get() = sp.getBoolean(KEY_PREMIUM, false)
        set(v) = sp.edit().putBoolean(KEY_PREMIUM, v).apply()

    /** Visas onboarding bara första gången. */
    var onboarded: Boolean
        get() = sp.getBoolean(KEY_ONBOARDED, false)
        set(v) = sp.edit().putBoolean(KEY_ONBOARDED, v).apply()

    var pipCx: Float
        get() = sp.getFloat(KEY_PIP_CX, 0.74f).coerceIn(0.05f, 0.95f)
        set(v) = sp.edit().putFloat(KEY_PIP_CX, v.coerceIn(0.05f, 0.95f)).apply()

    var pipCy: Float
        get() = sp.getFloat(KEY_PIP_CY, 0.24f).coerceIn(0.05f, 0.95f)
        set(v) = sp.edit().putFloat(KEY_PIP_CY, v.coerceIn(0.05f, 0.95f)).apply()

    var pipScale: Float
        get() = sp.getFloat(KEY_PIP_SCALE, 0.34f).coerceIn(0.16f, 0.62f)
        set(v) = sp.edit().putFloat(KEY_PIP_SCALE, v.coerceIn(0.16f, 0.62f)).apply()

    var swapCameras: Boolean
        get() = sp.getBoolean(KEY_SWAP, false)
        set(v) = sp.edit().putBoolean(KEY_SWAP, v).apply()

    var audioEnabled: Boolean
        get() = sp.getBoolean(KEY_AUDIO, true)
        set(v) = sp.edit().putBoolean(KEY_AUDIO, v).apply()

    // --- Mappar (enkel app-lokal organisation) ---------------------------------

    var folders: List<String>
        get() = readStringList(KEY_FOLDERS)
        set(v) = sp.edit().putString(KEY_FOLDERS, org.json.JSONArray(v).toString()).apply()

    var clipFolders: Map<Long, String>
        get() = readClipFolders()
        set(v) = sp.edit().putString(
            KEY_CLIP_FOLDERS,
            org.json.JSONObject().apply { v.forEach { (id, name) -> put(id.toString(), name) } }.toString()
        ).apply()

    fun addFolder(name: String): Boolean {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) return false
        if (folders.contains(clean)) return false
        folders = folders + clean
        return true
    }

    fun removeFolder(name: String) {
        val rest = clipFolders.toMutableMap()
        rest.values.remove(name)
        clipFolders = rest
        folders = folders.filter { it != name }
    }

    /** Flytta klipp till mapp; tom mapp = ingen mapp (tas bort från mappingen). */
    fun moveToFolder(ids: List<Long>, folder: String) {
        val map = clipFolders.toMutableMap()
        ids.forEach {
            if (folder.isEmpty()) map.remove(it) else map[it] = folder
        }
        clipFolders = map
    }

    fun folderOf(id: Long): String? = clipFolders[id]

    private fun readStringList(key: String): List<String> {
        val raw = sp.getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    private fun readClipFolders(): Map<Long, String> {
        val raw = sp.getString(KEY_CLIP_FOLDERS, null) ?: return emptyMap()
        return runCatching {
            val obj = org.json.JSONObject(raw)
            obj.keys().asSequence().associateWith { obj.getString(it) }
                .mapKeys { it.key.toLongOrNull() ?: -1L }
                .filterKeys { it >= 0 }
        }.getOrDefault(emptyMap())
    }

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_FRONT = "front_aspect"
        private const val KEY_BACK = "back_aspect"
        private const val KEY_QUALITY = "quality"
        private const val KEY_CODEC = "codec"
        private const val KEY_PREMIUM = "premium"
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_SETUP_DONE = "setup_done"
        private const val KEY_STUDIO_SYNC = "studio_sync_v1"
        private const val KEY_PIP_CX = "pip_cx"
        private const val KEY_PIP_CY = "pip_cy"
        private const val KEY_PIP_SCALE = "pip_scale"
        private const val KEY_SWAP = "swap_cameras"
        private const val KEY_AUDIO = "audio_enabled"
        private const val KEY_FOLDERS = "folders"
        private const val KEY_CLIP_FOLDERS = "clip_folders"
    }
}

/** Mappar AspectOption → riktiga CameraX AspectRatio-konstanter. */
fun AspectOption.toCxRatio(): Int = when (this) {
    AspectOption.R_9_16, AspectOption.R_16_9 -> AspectRatio.RATIO_16_9
    AspectOption.R_1_1, AspectOption.R_4_3 -> AspectRatio.RATIO_4_3
}
