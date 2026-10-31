package kr.co.nuni.gildam.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.followStore by preferencesDataStore(name = "follow")

/**
 * '코스 따라 걷기' 진행 상태 저장값.
 * 앱이 백그라운드에서 정리(프로세스 종료)돼도 같은 코스를 이어서 볼 수 있게 최소 정보만 남깁니다.
 * - [courseId]: 따라 걷던 코스 (경로·이름은 DB 에서 다시 읽음)
 * - [alongM]: 코스 시작점부터 마지막으로 확인한 위치까지 거리(m)
 * - [offsetM]: 마지막으로 확인한 경로선 이탈 거리(m)
 * - [offCourse]: 그때 이탈 상태였는지 (복원 직후 같은 이탈로 또 진동하지 않게)
 */
data class SavedFollow(
    val courseId: String,
    val alongM: Double,
    val offsetM: Double,
    val offCourse: Boolean
) {
    /** "코스id;진행m;이탈m;이탈여부" 한 줄로 저장 (한 키라서 쓰기·지우기가 한 번에 됨) */
    fun encode(): String = listOf(
        courseId,
        alongM.toString(),
        offsetM.toString(),
        offCourse.toString()
    ).joinToString(";")

    companion object {
        /** 저장값이 깨졌거나 코스 id 가 없으면 null (그냥 복원 안 함) */
        fun decode(raw: String?): SavedFollow? {
            val parts = raw?.split(';') ?: return null
            if (parts.size < 4) return null
            val courseId = parts.dropLast(3).joinToString(";").trim()
            val along = parts[parts.size - 3].toDoubleOrNull() ?: return null
            val offset = parts[parts.size - 2].toDoubleOrNull() ?: return null
            val off = parts.last().toBooleanStrictOrNull() ?: return null
            if (courseId.isEmpty()) return null
            return SavedFollow(courseId, along.coerceAtLeast(0.0), offset.coerceAtLeast(0.0), off)
        }
    }
}

/**
 * 따라 걷기 상태를 이 기기에 저장합니다 (서버로 보내지 않음).
 * 시작할 때 저장, 걷는 동안 가끔 갱신, 그만두기·코스 삭제·기록 종료 때 지웁니다.
 */
@Singleton
class FollowPrefs @Inject constructor(@ApplicationContext private val context: Context) {
    private val followKey = stringPreferencesKey("following_course")

    // 화면(ViewModel)이 바로 정리돼도 지우기가 취소되지 않도록 앱 수명 범위로 씀
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val saved: Flow<SavedFollow?> = context.followStore.data.map { SavedFollow.decode(it[followKey]) }

    suspend fun save(follow: SavedFollow) {
        context.followStore.edit { it[followKey] = follow.encode() }
    }

    suspend fun clear() {
        context.followStore.edit { it.remove(followKey) }
    }

    /** 지우기를 기다리지 않고 바로 요청 (따라 걷기 그만두기 등) */
    fun clearNow() {
        scope.launch { runCatching { clear() } }
    }
}
