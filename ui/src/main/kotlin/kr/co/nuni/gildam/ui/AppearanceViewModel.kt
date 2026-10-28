package kr.co.nuni.gildam.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kr.co.nuni.gildam.data.model.GaugeMetric
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.data.prefs.AppearancePrefs
import kr.co.nuni.gildam.data.prefs.GaugePrefs
import kr.co.nuni.gildam.ui.theme.DarkModePref
import kr.co.nuni.gildam.ui.theme.GildamPalette
import javax.inject.Inject
import kr.co.nuni.gildam.map.MapCameraMode

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val prefs: AppearancePrefs,
    private val gaugePrefs: GaugePrefs
) : ViewModel() {
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

    /** 활동 구분별 '지도 위 계기판' 선택 (저장값 없으면 그 활동의 기본값) */
    val gaugeSelections: StateFlow<Map<TransportType, GaugeMetric>> = gaugePrefs.selections
        .stateIn(viewModelScope, SharingStarted.Eagerly, TransportType.entries.associateWith { GaugeMetric.defaultFor(it) })

    fun setGaugeSelection(type: TransportType, metric: GaugeMetric) {
        viewModelScope.launch { gaugePrefs.setSelection(type, metric) }
    }

    fun setPalette(p: GildamPalette) { viewModelScope.launch { prefs.setPalette(p.name) } }
    fun setDarkMode(m: DarkModePref) { viewModelScope.launch { prefs.setDarkMode(m.name) } }
}
