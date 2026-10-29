package kr.co.nuni.gildam.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recordingStore by preferencesDataStore(name = "recording")

/** 기록 화면 설정(마지막 선택 이동 수단, 기록 중 화면 켜 두기 등)을 DataStore에 저장. */
@Singleton
class RecordingPrefs @Inject constructor(@ApplicationContext private val context: Context) {
    private val lastTransportKey = stringPreferencesKey("last_transport")
    private val keepScreenOnKey = booleanPreferencesKey("keep_screen_on")

    /** 마지막 선택 이동 수단 (WALK/RUN/HIKE/BIKE/CAR 이름) */
    val lastTransport: Flow<String?> = context.recordingStore.data.map { it[lastTransportKey] }
    suspend fun setLastTransport(name: String) { context.recordingStore.edit { it[lastTransportKey] = name } }

    /** 기록 중 화면 켜 두기 (기본값 OFF = false) */
    val keepScreenOn: Flow<Boolean> = context.recordingStore.data.map {
        it[keepScreenOnKey] ?: DEFAULT_KEEP_SCREEN_ON
    }
    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.recordingStore.edit { it[keepScreenOnKey] = enabled }
    }

    companion object {
        const val DEFAULT_KEEP_SCREEN_ON = false
    }
}
