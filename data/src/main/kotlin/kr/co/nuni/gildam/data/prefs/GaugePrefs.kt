package kr.co.nuni.gildam.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kr.co.nuni.gildam.data.model.GaugeLayout
import kr.co.nuni.gildam.data.model.GaugeMetric
import kr.co.nuni.gildam.data.model.TransportType
import javax.inject.Inject
import javax.inject.Singleton

private val Context.gaugeStore by preferencesDataStore(name = "gauge")

/**
 * 활동 구분별 계기판 구성(순서+켜기/끄기) 과 지도 위 강조 지표를 저장합니다.
 * 저장한 값이 없으면 기본값을 씁니다.
 */
@Singleton
class GaugePrefs @Inject constructor(@ApplicationContext private val context: Context) {
    private fun overlayKey(type: TransportType) = stringPreferencesKey("overlay_${type.name}")
    private fun layoutKey(type: TransportType) = stringPreferencesKey("layout_${type.name}")

    private fun resolveOverlay(name: String?, type: TransportType): GaugeMetric =
        GaugeMetric.entries.firstOrNull { it.name == name } ?: GaugeMetric.defaultFor(type)

    /** 활동 구분 5개 전부의 지도 강조 지표 (저장값 없으면 기본값) */
    val selections: Flow<Map<TransportType, GaugeMetric>> = context.gaugeStore.data.map { prefs ->
        TransportType.entries.associateWith { type -> resolveOverlay(prefs[overlayKey(type)], type) }
    }

    suspend fun setSelection(type: TransportType, metric: GaugeMetric) {
        context.gaugeStore.edit { it[overlayKey(type)] = metric.name }
    }

    suspend fun resetSelection(type: TransportType) {
        context.gaugeStore.edit { it.remove(overlayKey(type)) }
    }

    suspend fun resetAllOverlays() {
        context.gaugeStore.edit { prefs ->
            TransportType.entries.forEach { prefs.remove(overlayKey(it)) }
        }
    }

    /** 활동 구분별 계기판 구성 (저장값 없으면 기본) */
    val layouts: Flow<Map<TransportType, GaugeLayout>> = context.gaugeStore.data.map { prefs ->
        TransportType.entries.associateWith { type ->
            GaugeLayout.decode(prefs[layoutKey(type)])
        }
    }

    suspend fun setLayout(type: TransportType, layout: GaugeLayout) {
        context.gaugeStore.edit { it[layoutKey(type)] = layout.encode() }
    }

    suspend fun resetLayout(type: TransportType) {
        context.gaugeStore.edit { it.remove(layoutKey(type)) }
    }

    suspend fun resetAllLayouts() {
        context.gaugeStore.edit { prefs ->
            TransportType.entries.forEach { prefs.remove(layoutKey(it)) }
        }
    }

    // Legacy global layout (kept for migration; new code uses per-type)
    private val legacyLayoutKey = stringPreferencesKey("grid_layout")

    val legacyGridLayout: Flow<GaugeLayout> = context.gaugeStore.data.map { GaugeLayout.decode(it[legacyLayoutKey]) }

    suspend fun clearLegacyLayout() {
        context.gaugeStore.edit { it.remove(legacyLayoutKey) }
    }
}
