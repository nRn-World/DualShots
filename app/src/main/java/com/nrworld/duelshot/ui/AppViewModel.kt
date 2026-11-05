package com.nrworld.duelshot.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.nrworld.duelshot.data.AppPrefs
import com.nrworld.duelshot.model.AspectOption
import com.nrworld.duelshot.model.CodecOption
import com.nrworld.duelshot.model.QualityOption
import com.nrworld.duelshot.model.RecordingMode

/**
 * Håller appens inställningar som observerbar state och speglar dem till prefs.
 * Delas av alla skärmar via activityViewModels. Egenskapernas setters persistrar,
 * kvalitet/codec är premium-låsta.
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
    /** Kvalitet går bara att ändra om premium (eller valet är gratis). */
    var quality: QualityOption
        get() = qualityState
        set(v) {
            if (!v.premium || isPremium) { qualityState = v; prefs.quality = v }
        }

    private var codecState by mutableStateOf(prefs.codec)
    var codec: CodecOption
        get() = codecState
        set(v) {
            if (!v.premium || isPremium) { codecState = v; prefs.codec = v }
        }

    private var saveAspectState by mutableStateOf(prefs.saveAspect)
    /** Format på Dual View-filen; null = Auto (följer bakkameran). */
    var saveAspect: AspectOption?
        get() = saveAspectState
        set(v) { saveAspectState = v; prefs.saveAspect = v }

    private var stabilizeState by mutableStateOf(prefs.stabilize)
    var stabilize: Boolean
        get() = stabilizeState
        set(v) { stabilizeState = v; prefs.stabilize = v }

    private var gridState by mutableStateOf(prefs.grid)
    var grid: Boolean
        get() = gridState
        set(v) { gridState = v; prefs.grid = v }

    private var audioState by mutableStateOf(prefs.recordAudio)
    var recordAudio: Boolean
        get() = audioState
        set(v) { audioState = v; prefs.recordAudio = v }

    private var locationState by mutableStateOf(prefs.saveLocation)
    var saveLocation: Boolean
        get() = locationState
        set(v) { locationState = v; prefs.saveLocation = v }

    private var premiumState by mutableStateOf(prefs.isPremium)
    var isPremium: Boolean
        get() = premiumState
        set(v) { premiumState = v; prefs.isPremium = v }

    /** Mockat premium-köp – sätter flaggan direkt. Byt mot Play Billing sen. */
    fun grantPremium() { isPremium = true }
}
