package com.nrworld.duelshot.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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

    private var premiumState by mutableStateOf(prefs.isPremium)
    var isPremium: Boolean
        get() = premiumState
        set(v) { premiumState = v; prefs.isPremium = v }

    private var pipCxState by mutableFloatStateOf(prefs.pipCx)
    var pipCx: Float
        get() = pipCxState
        set(v) { pipCxState = v; prefs.pipCx = v }

    private var pipCyState by mutableFloatStateOf(prefs.pipCy)
    var pipCy: Float
        get() = pipCyState
        set(v) { pipCyState = v; prefs.pipCy = v }

    private var pipScaleState by mutableFloatStateOf(prefs.pipScale)
    var pipScale: Float
        get() = pipScaleState
        set(v) { pipScaleState = v; prefs.pipScale = v }

    private var swapState by mutableStateOf(prefs.swapCameras)
    var swapCameras: Boolean
        get() = swapState
        set(v) { swapState = v; prefs.swapCameras = v }

    private var audioState by mutableStateOf(prefs.audioEnabled)
    var audioEnabled: Boolean
        get() = audioState
        set(v) { audioState = v; prefs.audioEnabled = v }

    /** Mockat premium-köp – sätter flaggan direkt. Byt mot Play Billing sen. */
    fun grantPremium() { isPremium = true }
}
