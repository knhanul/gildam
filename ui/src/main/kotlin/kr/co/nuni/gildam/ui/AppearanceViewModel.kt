package kr.co.nuni.gildam.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kr.co.nuni.gildam.data.list.RecordGroupMode
import kr.co.nuni.gildam.data.model.GaugeLayout
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

    /** 기록 보관함: 최근순 / 월별 / 지역별 (기본 최근순) */
    val recordGroupMode: StateFlow<RecordGroupMode> = prefs.recordGroupMode
        .map { n -> RecordGroupMode.fromName(n) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RecordGroupMode.RECENT)
    fun setRecordGroupMode(mode: RecordGroupMode) { viewModelScope.launch { prefs.setRecordGroupMode(mode.name) } }

    /** 기록 보관함: 아주 짧은 기록 숨기기 (기본 꺼짐) */
    val hideShortRecords: StateFlow<Boolean> = prefs.hideShortRecords
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun setHideShortRecords(hide: Boolean) { viewModelScope.launch { prefs.setHideShortRecords(hide) } }

    /** 활동 구분별 '지도 위 계기판' 선택 (저장값 없으면 그 활동의 기본값) */
    val gaugeSelections: StateFlow<Map<TransportType, GaugeMetric>> = gaugePrefs.selections
        .stateIn(viewModelScope, SharingStarted.Eagerly, TransportType.entries.associateWith { GaugeMetric.defaultFor(it) })

    fun setGaugeSelection(type: TransportType, metric: GaugeMetric) {
        viewModelScope.launch { gaugePrefs.setSelection(type, metric) }
    }

    /** 활동 구분별 계기판 구성 */
    val gaugeLayouts: StateFlow<Map<TransportType, GaugeLayout>> = gaugePrefs.layouts
        .stateIn(viewModelScope, SharingStarted.Eagerly, TransportType.entries.associateWith { GaugeLayout.DEFAULT })

    fun setGaugeLayout(type: TransportType, layout: GaugeLayout) {
        viewModelScope.launch {
            if (layout == GaugeLayout.DEFAULT) gaugePrefs.resetLayout(type) else gaugePrefs.setLayout(type, layout)
        }
    }

    fun resetGaugeLayout(type: TransportType) { viewModelScope.launch { gaugePrefs.resetLayout(type) } }

    fun setPalette(p: GildamPalette) { viewModelScope.launch { prefs.setPalette(p.name) } }
    fun setDarkMode(m: DarkModePref) { viewModelScope.launch { prefs.setDarkMode(m.name) } }
}
