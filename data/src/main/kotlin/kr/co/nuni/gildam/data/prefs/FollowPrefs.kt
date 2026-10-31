package kr.co.nuni.gildam.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * '코스 따라 걷기' 진행 상태 저장값.
 * 앱이 백그라운드에서 정리(프로세스 종료)돼도 같은 코스를 이어서 볼 수 있게 최소 정보만 남깁니다.
 * - [sessionId]: 이번 따라 걷기를 구분하는 값 (그만두기·복원 판단용, 같은 코스를 다시 골라도 새 값)
 * - [courseId]: 따라 걷던 코스 (경로·이름은 DB 에서 다시 읽음)
 * - [alongM]: 코스 시작점부터 마지막으로 확인한 위치까지 거리(m)
 * - [offsetM]: 마지막으로 확인한 경로선 이탈 거리(m)
 * - [offCourse]: 그때 이탈 상태였는지 (복원 직후 같은 이탈로 또 진동하지 않게)
 * - [activityId]: 이 따라 걷기가 붙어 있던 기록 id. 미완료 기록을 두고 '새로 기록'을 시작하면
 *   id 가 달라지므로 그 코스가 되살아나지 않습니다. (예전 저장값은 null = 확인 안 함)
 */
data class SavedFollow(
    val courseId: String,
    val alongM: Double,
    val offsetM: Double,
    val offCourse: Boolean,
    val sessionId: String = "",
    val activityId: String? = null
) {
    /** "코스id;진행m;이탈m;이탈여부;세션id;기록id" 한 줄로 저장 */
    fun encode(): String = listOf(
        courseId,
        alongM.toString(),
        offsetM.toString(),
        offCourse.toString(),
        sessionId,
        activityId ?: ""
    ).joinToString(";")

    companion object {
        /**
         * 저장값이 깨졌거나 코스 id 가 없으면 null (그냥 복원 안 함).
         * 6칸(코스id;진행;이탈;이탈여부;세션id;기록id)이 현재 형식이고, 예전 4·5칸 저장값도 읽습니다.
         */
        fun decode(raw: String?): SavedFollow? {
            val p = raw?.split(';') ?: return null
            if (p.size !in 4..6) return null
            val courseId = p[0].trim()
            val along = p[1].toDoubleOrNull() ?: return null
            val offset = p[2].toDoubleOrNull() ?: return null
            val off = p[3].toBooleanStrictOrNull() ?: return null
            val session = p.getOrNull(4) ?: ""
            val activity = p.getOrNull(5)?.takeIf { it.isNotBlank() }
            if (courseId.isEmpty()) return null
            return SavedFollow(courseId, along.coerceAtLeast(0.0), offset.coerceAtLeast(0.0), off, session, activity)
        }
    }
}

/**
 * 따라 걷기 상태를 이 기기에 저장합니다 (서버로 보내지 않음).
 *
 * SharedPreferences 를 쓰는 이유: 'X 로 그만두기'처럼 되돌릴 수 없는 동작은 [clearNow] 로
 * **화면이 바뀌기 전에 디스크에 확정**(commit)해야, 그 직후 앱이 종료돼도 코스가 되살아나지 않습니다.
 * 걷는 중 진행 저장은 [saveProgress] (apply, 비동기)로 충분합니다.
 */
@Singleton
class FollowPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("follow", Context.MODE_PRIVATE)

    /** 저장된 따라 걷기 (없거나 비었으면 null) */
    fun saved(): SavedFollow? = SavedFollow.decode(prefs.getString(KEY, null))

    /** 시작처럼 '이후 복원 판단'에 영향을 주는 쓰기: 디스크에 확정한 뒤 true */
    fun saveNow(follow: SavedFollow): Boolean = prefs.edit().putString(KEY, follow.encode()).commit()

    /** 걷는 중 진행 저장: 화면을 막지 않도록 비동기 */
    fun saveProgress(follow: SavedFollow) {
        prefs.edit().putString(KEY, follow.encode()).apply()
    }

    /**
     * 따라 걷기 저장값을 지웁니다. commit 이라 이 함수가 true 를 돌려준 뒤에는
     * 프로세스가 바로 죽어도 값이 남지 않습니다(코스가 다시 살아나지 않음).
     */
    fun clearNow(): Boolean = prefs.edit().remove(KEY).commit()

    companion object {
        private const val KEY = "following_course"
    }
}

/**
 * 저장된 따라 걷기를 되살릴지 결정합니다 (순수 함수 → 단위 테스트).
 * - 저장값이 없으면(= X 로 그만뒀거나 지웠음) 절대 되살리지 않음
 * - 기록이 살아 있거나 미완료 기록이 남아 있을 때만
 * - 코스가 아직 있을 때만
 * - 그 코스가 붙어 있던 기록과 지금 기록이 같은 때만 (미완료 기록을 두고 '새로 기록'을 시작하면 다름)
 */
object FollowRestorePolicy {
    fun shouldRestore(
        saved: SavedFollow?,
        recordingAlive: Boolean,
        courseExists: Boolean,
        currentActivityId: String? = null
    ): Boolean {
        if (saved == null || !recordingAlive || !courseExists) return false
        val bound = saved.activityId ?: return true      // 예전 저장값은 기록 확인 없이 복원
        return bound == currentActivityId
    }

    /** 기록이 켜졌을 때 지금 따라 걷기를 그 기록에 붙일지, 예전 기록 것이라 정리할지 */
    enum class OnRecordingStart { BIND, CLEAR }

    /**
     * - 따라 걷던 기록이 따로 있었고(bound != null) 지금 기록이 다르면 → CLEAR
     *   (미완료 기록을 두고 '새로 기록'을 시작한 경우: 예전 코스는 끝난 것으로 봄)
     * - 아직 기록에 붙지 않았거나(bound == null) 같은 기록이면 → BIND
     */
    fun onRecordingStart(boundActivityId: String?, activeActivityId: String?): OnRecordingStart =
        if (boundActivityId != null && activeActivityId != null && boundActivityId != activeActivityId) OnRecordingStart.CLEAR
        else OnRecordingStart.BIND
}
