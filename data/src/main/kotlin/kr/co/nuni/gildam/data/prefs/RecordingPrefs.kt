package kr.co.nuni.gildam.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recordingStore by preferencesDataStore(name = "recording")

/** 기록 화면에서 마지막으로 선택한 이동 수단(TransportType 이름)을 저장. 값이 없으면 null. */
@Singleton
class RecordingPrefs @Inject constructor(@ApplicationContext private val context: Context) {
    private val lastTransportKey = stringPreferencesKey("last_transport")

    /** 마지막 선택 이동 수단 (WALK/RUN/HIKE/BIKE/CAR 이름) */
    val lastTransport: Flow<String?> = context.recordingStore.data.map { it[lastTransportKey] }
    suspend fun setLastTransport(name: String) { context.recordingStore.edit { it[lastTransportKey] = name } }
}
