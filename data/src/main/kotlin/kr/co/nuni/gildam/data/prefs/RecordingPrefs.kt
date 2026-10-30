package kr.co.nuni.gildam.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recordingStore by preferencesDataStore(name = "recording")

/** 기록 화면 설정(마지막 선택 이동 수단, 기록 중 화면 켜 두기, 자동 종료 기준 등)을 DataStore에 저장. */
@Singleton
class RecordingPrefs @Inject constructor(@ApplicationContext private val context: Context) {
    private val lastTransportKey = stringPreferencesKey("last_transport")
    private val keepScreenOnKey = booleanPreferencesKey("keep_screen_on")
    private val maxDurationMsKey = longPreferencesKey("max_duration_ms")
    private val idleLimitMsKey = longPreferencesKey("idle_limit_ms")
    private val graceMsKey = longPreferencesKey("grace_ms")

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

    /** 최대 기록 시간(ms). null = 사용 안 함. 목록에 없는 값이면 기본값(4시간)으로 대체. */
    val maxDurationMs: Flow<Long?> = context.recordingStore.data.map { prefs ->
        when (val raw = prefs[maxDurationMsKey]) {
            null -> DEFAULT_MAX_DURATION_MS
            DISABLED_SENTINEL_MS -> null
            in ALLOWED_MAX_DURATION_MS -> raw
            else -> DEFAULT_MAX_DURATION_MS
        }
    }

    suspend fun setMaxDurationMs(durationMs: Long?) {
        val normalized = if (durationMs == null) {
            DISABLED_SENTINEL_MS
        } else if (durationMs in ALLOWED_MAX_DURATION_MS) {
            durationMs
        } else {
            DEFAULT_MAX_DURATION_MS
        }
        context.recordingStore.edit { it[maxDurationMsKey] = normalized }
    }

    /** 오래 멈춤 기준(ms). null = 사용 안 함. 목록에 없는 값이면 기본값(20분)으로 대체. */
    val idleLimitMs: Flow<Long?> = context.recordingStore.data.map { prefs ->
        when (val raw = prefs[idleLimitMsKey]) {
            null -> DEFAULT_IDLE_LIMIT_MS
            DISABLED_SENTINEL_MS -> null
            in ALLOWED_IDLE_LIMIT_MS -> raw
            else -> DEFAULT_IDLE_LIMIT_MS
        }
    }

    suspend fun setIdleLimitMs(limitMs: Long?) {
        val normalized = if (limitMs == null) {
            DISABLED_SENTINEL_MS
        } else if (limitMs in ALLOWED_IDLE_LIMIT_MS) {
            limitMs
        } else {
            DEFAULT_IDLE_LIMIT_MS
        }
        context.recordingStore.edit { it[idleLimitMsKey] = normalized }
    }

    /** 확인 대기 시간(ms). 목록에 없는 값이면 기본값(10분)으로 대체. */
    val graceMs: Flow<Long> = context.recordingStore.data.map { prefs ->
        val raw = prefs[graceMsKey]
        if (raw != null && raw in ALLOWED_GRACE_MS) raw else DEFAULT_GRACE_MS
    }

    suspend fun setGraceMs(graceMs: Long) {
        val normalized = if (graceMs in ALLOWED_GRACE_MS) graceMs else DEFAULT_GRACE_MS
        context.recordingStore.edit { it[graceMsKey] = normalized }
    }

    companion object {
        const val DEFAULT_KEEP_SCREEN_ON = false

        private const val MINUTE_MS = 60_000L
        private const val HOUR_MS = 3_600_000L
        private const val DISABLED_SENTINEL_MS = -1L

        const val DEFAULT_MAX_DURATION_MS: Long = 4 * HOUR_MS
        const val DEFAULT_IDLE_LIMIT_MS: Long = 20 * MINUTE_MS
        const val DEFAULT_GRACE_MS: Long = 10 * MINUTE_MS

        val ALLOWED_MAX_DURATION_MS: Set<Long> = setOf(
            2 * HOUR_MS,
            4 * HOUR_MS,
            6 * HOUR_MS,
            8 * HOUR_MS,
            10 * HOUR_MS
        )

        val ALLOWED_IDLE_LIMIT_MS: Set<Long> = setOf(
            10 * MINUTE_MS,
            20 * MINUTE_MS,
            30 * MINUTE_MS,
            40 * MINUTE_MS,
            50 * MINUTE_MS,
            60 * MINUTE_MS
        )

        val ALLOWED_GRACE_MS: Set<Long> = setOf(
            5 * MINUTE_MS,
            10 * MINUTE_MS,
            15 * MINUTE_MS
        )
    }
}
