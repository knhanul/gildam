package kr.co.nuni.gildam.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kr.co.nuni.gildam.data.model.GaugeMetric
import kr.co.nuni.gildam.data.model.TransportType
import javax.inject.Inject
import javax.inject.Singleton

private val Context.gaugeStore by preferencesDataStore(name = "gauge")

/**
 * 활동 구분별 '지도 위 계기판' 선택을 저장합니다.
 * 저장한 값이 없거나 알 수 없는 값이면 그 활동의 기본값([GaugeMetric.defaultFor])을 씁니다.
 */
@Singleton
class GaugePrefs @Inject constructor(@ApplicationContext private val context: Context) {
    private fun key(type: TransportType) = stringPreferencesKey("overlay_${type.name}")

    private fun resolve(name: String?, type: TransportType): GaugeMetric =
        GaugeMetric.entries.firstOrNull { it.name == name } ?: GaugeMetric.defaultFor(type)

    /** 활동 구분 5개 전부의 선택 (저장값이 없으면 기본값) */
    val selections: Flow<Map<TransportType, GaugeMetric>> = context.gaugeStore.data.map { prefs ->
        TransportType.entries.associateWith { type -> resolve(prefs[key(type)], type) }
    }

    suspend fun setSelection(type: TransportType, metric: GaugeMetric) {
        context.gaugeStore.edit { it[key(type)] = metric.name }
    }
}
