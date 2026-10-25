package kr.co.nuni.gildam.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kr.co.nuni.gildam.data.prefs.AppearancePrefs
import kr.co.nuni.gildam.ui.theme.DarkModePref
import kr.co.nuni.gildam.ui.theme.GildamPalette
import javax.inject.Inject
import kr.co.nuni.gildam.map.MapCameraMode

@HiltViewModel
class AppearanceViewModel @Inject constructor(private val prefs: AppearancePrefs) : ViewModel() {
    val palette: StateFlow<GildamPalette> = prefs.palette
        .map { n -> GildamPalette.entries.firstOrNull { it.name == n } ?: GildamPalette.DEFAULT }
        .stateIn(viewModelScope, SharingStarted.Eagerly, GildamPalette.DEFAULT)

    val darkMode: StateFlow<DarkModePref> = prefs.darkMode
        .map { n -> DarkModePref.entries.firstOrNull { it.name == n } ?: DarkModePref.SYSTEM }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DarkModePref.SYSTEM)

    val recordMapMode: StateFlow<MapCameraMode> = prefs.recordMapMode
        .map { n -> MapCameraMode.entries.firstOrNull { it.name == n } ?: MapCameraMode.DEFAULT }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MapCameraMode.DEFAULT)
    fun setRecordMapMode(m: MapCameraMode) { viewModelScope.launch { prefs.setRecordMapMode(m.name) } }

    fun setPalette(p: GildamPalette) { viewModelScope.launch { prefs.setPalette(p.name) } }
    fun setDarkMode(m: DarkModePref) { viewModelScope.launch { prefs.setDarkMode(m.name) } }
}
