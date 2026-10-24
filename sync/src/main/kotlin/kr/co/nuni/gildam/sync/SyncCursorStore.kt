package kr.co.nuni.gildam.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 마지막으로 받은 서버 cursor(server_seq) 저장. 계정(sub)이 바뀌면 0부터 다시 받습니다. 비밀값 아님. */
@Singleton
class SyncCursorStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("gildam_sync_state", Context.MODE_PRIVATE)

    fun get(sub: String): Long = if (prefs.getString(KEY_SUB, null) == sub) prefs.getLong(KEY_CURSOR, 0L) else 0L

    fun set(sub: String, cursor: Long) { prefs.edit().putString(KEY_SUB, sub).putLong(KEY_CURSOR, cursor).apply() }

    fun clear() { prefs.edit().clear().apply() }

    private companion object { const val KEY_SUB = "sub"; const val KEY_CURSOR = "cursor" }
}
