package com.nrworld.duelshot.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.nrworld.duelshot.data.AppPrefs
import com.nrworld.duelshot.model.AspectOption
import com.nrworld.duelshot.model.CodecOption
import com.nrworld.duelshot.model.PipStyle
import com.nrworld.duelshot.model.QualityOption
import com.nrworld.duelshot.model.RecordingMode

/**
 * Håller appens inställningar som observerbar state och speglar dem till prefs.
 * Delas av alla skärmar via activityViewModels. Egenskapernas setters persistrar.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPrefs(app)

    private var modeState by mutableStateOf(prefs.mode)
    var mode: RecordingMode
        get() = modeState
        set(v) { modeState = v; prefs.mode = v }

    private var frontState by mutableStateOf(prefs.frontAspect)
    var frontAspect: AspectOption
        get() = frontState
        set(v) { frontState = v; prefs.frontAspect = v }

    private var backState by mutableStateOf(prefs.backAspect)
    var backAspect: AspectOption
        get() = backState
        set(v) { backState = v; prefs.backAspect = v }

    private var qualityState by mutableStateOf(prefs.quality)
    var quality: QualityOption
        get() = qualityState
        set(v) { qualityState = v; prefs.quality = v }

    private var codecState by mutableStateOf(prefs.codec)
    var codec: CodecOption
        get() = codecState
        set(v) { codecState = v; prefs.codec = v }

    private var saveAspectState by mutableStateOf(prefs.saveAspect)
    /** Format på Dual View-filen; null = Auto (följer bakkameran). */
    var saveAspect: AspectOption?
        get() = saveAspectState
        set(v) { saveAspectState = v; prefs.saveAspect = v }

    private var gridState by mutableStateOf(prefs.grid)
    var grid: Boolean
        get() = gridState
        set(v) { gridState = v; prefs.grid = v }

    private var audioState by mutableStateOf(prefs.recordAudio)
    var recordAudio: Boolean
        get() = audioState
        set(v) { audioState = v; prefs.recordAudio = v }

    private var countdownState by mutableStateOf(prefs.countdownSec)
    var countdownSec: Int
        get() = countdownState
        set(v) { countdownState = v; prefs.countdownSec = v }

    private var pipStyleState by mutableStateOf(prefs.pipStyle)
    var pipStyle: PipStyle
        get() = pipStyleState
        set(v) { pipStyleState = v; prefs.pipStyle = v }

    private var favoritesState by mutableStateOf(prefs.favorites)
    val favorites: Set<String> get() = favoritesState

    fun toggleFavorite(key: String) {
        val next = if (key in favoritesState) favoritesState - key else favoritesState + key
        favoritesState = next
        prefs.favorites = next
    }
}
