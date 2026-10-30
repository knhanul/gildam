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

private val Context.profileStore by preferencesDataStore(name = "profile")

/**
 * 길담이 저장하는 유일한 개인 정보: 카카오(NUNI ID) 닉네임.
 * 사용자가 앱에서 바꾼 닉네임을 계정(sub)별로 이 기기에만 저장합니다. (서버 수정 API가 생기면 그쪽으로 옮김)
 */
@Singleton
class ProfilePrefs @Inject constructor(@ApplicationContext private val context: Context) {
    private fun key(sub: String) = stringPreferencesKey("nickname_$sub")

    fun nickname(sub: String): Flow<String?> = context.profileStore.data.map { it[key(sub)] }

    suspend fun setNickname(sub: String, nickname: String) {
        context.profileStore.edit { it[key(sub)] = nickname }
    }

    companion object {
        const val MAX_LENGTH = 20

        /** 앞뒤 공백 제거 후 1~20자면 그 값, 아니면 null */
        fun normalize(raw: String): String? = raw.trim().takeIf { it.length in 1..MAX_LENGTH }
    }
}
