package kr.co.nuni.gildam.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 마지막으로 받은 서버 cursor(server_seq) 저장. 계정(sub)이 바뀌면 0부터 다시 받습니다. 비밀값 아님. */
@Singleton
class SyncCursorStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("gildam_sync_state", Context.MODE_PRIVATE)

    fun get(sub: String): Long = if (prefs.getString(KEY_SUB, null) == sub) prefs.getLong(KEY_CURSOR, 0L) else 0L

    fun set(sub: String, cursor: Long) { prefs.edit().putString(KEY_SUB, sub).putLong(KEY_CURSOR, cursor).apply() }

    private val _lastSuccessAtMs = MutableStateFlow(prefs.getLong(KEY_LAST_SUCCESS_AT, 0L))
    /** 마지막으로 동기화가 성공한 시각(epoch ms). 한 번도 성공한 적 없으면 0. */
    val lastSuccessAtMs: StateFlow<Long> = _lastSuccessAtMs.asStateFlow()

    fun markSyncSuccess(nowMs: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_SUCCESS_AT, nowMs).apply()
        _lastSuccessAtMs.value = nowMs
    }

    fun clear() {
        prefs.edit().clear().apply()
        _lastSuccessAtMs.value = 0L
    }

    private companion object {
        const val KEY_SUB = "sub"
        const val KEY_CURSOR = "cursor"
        const val KEY_LAST_SUCCESS_AT = "last_success_at"
    }
}
