package kr.co.nuni.gildam.ui

import kr.co.nuni.gildam.ui.l10n.*
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.co.nuni.gildam.data.course.CourseEntity
import kr.co.nuni.gildam.data.course.CourseFollower
import kr.co.nuni.gildam.data.course.CourseRepository
import kr.co.nuni.gildam.data.course.FollowActivityGuess
import kr.co.nuni.gildam.data.gpxcompare.GpxRuggedness
import kr.co.nuni.gildam.data.gpxcompare.RuggednessResult
import kr.co.nuni.gildam.data.prefs.FollowPrefs
import kr.co.nuni.gildam.data.prefs.FollowRestorePolicy
import kr.co.nuni.gildam.data.prefs.FollowRestorePolicy.OnRecordingStart
import kr.co.nuni.gildam.data.prefs.SavedFollow
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.data.repository.GildamRepository
import kr.co.nuni.gildam.recording.LatLonPoint
import kr.co.nuni.gildam.recording.RecordingController
import kr.co.nuni.gildam.recording.TrackSimplifier
import java.util.UUID
import kotlin.math.abs
import kr.co.nuni.gildam.ui.screens.CourseDetailUi
import kr.co.nuni.gildam.ui.screens.CourseFollowUi
import kr.co.nuni.gildam.ui.screens.CourseListItem
import javax.inject.Inject

@HiltViewModel
class CourseViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val courses: CourseRepository,
    private val activities: GildamRepository,
    private val followPrefs: FollowPrefs,
    private val recordingController: RecordingController
) : ViewModel() {

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    init {
        viewModelScope.launch { restoreSavedFollow() }
    }

    val courseList: StateFlow<List<CourseListItem>> = courses.observeCourses().map { list ->
        list.map { it.toListItem() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun observeDetail(id: String): Flow<CourseDetailUi?> = courses.observeCourse(id).map { c ->
        c?.let {
            val pts = it.points()
            val revPts = pts.reversed()
            val (r, revR) = withContext(Dispatchers.Default) {
                val fwd = GpxRuggedness.analyze(it.name, pts)
                val rev = if (revPts.size >= 2) GpxRuggedness.analyze(it.name, revPts) else fwd
                fwd to rev
            }
            val route = pts.map { p -> LatLonPoint(p.lat, p.lon) }
            CourseDetailUi(
                id = it.id,
                name = it.name,
                result = r,
                route = route,
                reversedResult = revR,
                reversedRoute = route.asReversed()
            )
        }
    }

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch { _importing.value = true; try { courses.importFromUris(uris) } finally { _importing.value = false } }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            courses.delete(id)
            // 지운 코스를 따라 걷던 중이었으면 따라 걷기도 끝내고 저장값도 지움
            if (_follow.value?.id == id) stopFollow()
            else if (followPrefs.saved()?.courseId == id) followPrefs.clearNow()
        }
    }

    // ---- 코스 따라가기 ----
    private data class Following(
        val id: String,
        val name: String,
        val result: RuggednessResult,
        val route: List<LatLonPoint>,
        val follower: CourseFollower,
        val sessionId: String,
        /** 이 따라가기가 붙어 있는 기록 id (아직 없으면 null) */
        val boundActivityId: String? = null,
        val reversed: Boolean = false
    )
    private val _follow = MutableStateFlow<Following?>(null)

    /**
     * '따라가기' 활동 기본값: 마지막으로 고른 활동이 있으면 그 값, 없으면 코스 모양으로 추정.
     * (코스 상세의 활동 선택 시트가 처음 열릴 때 씀)
     */
    fun suggestedFollowActivity(id: String): Flow<TransportType> = courses.observeCourse(id).map { c ->
        val saved = followPrefs.lastActivity()?.let { n -> TransportType.entries.firstOrNull { it.name == n } }
        if (saved != null || c == null) return@map saved ?: TransportType.WALK
        val pts = c.points()
        val r = withContext(Dispatchers.Default) { GpxRuggedness.analyze(c.name, pts) }
        FollowActivityGuess.guess(r)
    }

    /** 사용자가 고른 따라가기 활동을 기억 (다음에 기본값으로) */
    fun rememberFollowActivity(type: TransportType) = followPrefs.setLastActivity(type.name)

    fun startFollow(id: String, reversed: Boolean = false) {
        viewModelScope.launch {
            val c = courses.getCourse(id) ?: return@launch
            val rawPts = c.points()
            val pts = if (reversed) rawPts.reversed() else rawPts
            val (r, route) = withContext(Dispatchers.Default) { GpxRuggedness.analyze(c.name, pts) to displayRoute(pts) }
            val newSessionId = UUID.randomUUID().toString()
            val activeId = activities.getActiveRecording()?.id
            _follow.value = Following(c.id, c.name, r, route, CourseFollower(pts), newSessionId, activeId, reversed)
            // 기록 중이면 앱이 정리돼도 다시 살아나도록 저장 (기록 없이 코스만 볼 때는 저장하지 않음)
            if (activeId != null || recordingController.liveState.value.let { it.isRecording || it.isPaused }) {
                followPrefs.saveNow(SavedFollow(c.id, 0.0, 0.0, false, newSessionId, activeId, reversed))
            }
        }
    }

    /**
     * 따라 걷기 그만두기 (X). 저장값을 **먼저 디스크에서 지운 뒤** 화면에서 카드를 닫습니다.
     * 지우기가 실패하면 카드를 닫지 않고 그대로 둬서, '닫혔는데 다시 살아나는' 상태를 만들지 않습니다.
     */
    fun stopFollow() {
        if (followPrefs.clearNow()) _follow.value = null
    }

    /** 기록이 끝났을 때처럼 실패해도 화면은 닫아야 하는 경우: 디스크 정리를 먼저 시도하고 카드는 닫음 */
    private fun endFollowAfterRecording() {
        followPrefs.clearNow()
        _follow.value = null
    }

    /** 지금 기록이 살아 있는지: 서비스 상태(같은 프로세스) 또는 DB 의 미완료 기록 */
    private suspend fun isRecordingNow(): Boolean =
        recordingController.liveState.value.let { it.isRecording || it.isPaused } ||
            activities.getActiveRecording() != null

    /**
     * 앱이 백그라운드에서 정리(프로세스 종료)됐다 돌아왔을 때, 저장해 둔 코스 따라 걷기를 되살립니다.
     * 기록이 살아 있거나 미완료 기록이 남아 있을 때만 되살리고, 아니면 저장값을 지웁니다.
     */
    private suspend fun restoreSavedFollow() {
        val saved = followPrefs.saved() ?: return          // 그만뒀거나 지운 상태면 절대 되살리지 않음
        val recordingAlive = isRecordingNow()
        val activeId = if (recordingAlive) activities.getActiveRecording()?.id else null
        val c = if (recordingAlive) courses.getCourse(saved.courseId) else null
        if (!FollowRestorePolicy.shouldRestore(saved, recordingAlive, c != null, activeId)) {
            followPrefs.clearNow()                          // 기록이 끝났거나 코스가 지워짐 → 저장값 정리
            return
        }
        if (_follow.value != null) return
        val rawPts = c!!.points()
        val pts = if (saved.reversed) rawPts.reversed() else rawPts
        val (r, route) = withContext(Dispatchers.Default) { GpxRuggedness.analyze(c.name, pts) to displayRoute(pts) }
        val follower = CourseFollower(pts).also {
            it.restoreState(saved.alongM, saved.offsetM, saved.offCourse)
        }
        _follow.value = Following(c.id, c.name, r, route, follower, saved.sessionId, saved.activityId, saved.reversed)
    }

    /** 기록 화면 지도에 그릴 코스 선: 화면용으로 점을 줄임 (남은 거리·이탈 계산은 원본 [CourseFollower] 가 함) */
    private fun displayRoute(pts: List<kr.co.nuni.gildam.data.gpxcompare.GpxPoint>): List<LatLonPoint> =
        TrackSimplifier.simplifyToLimit(pts.map { LatLonPoint(it.lat, it.lon) }).first

    /**
     * 따라 걷는 동안 진행 상황을 가끔 저장하고(25m 이상 진행 또는 이탈 상태 변화),
     * 기록이 끝나면 따라 걷기를 정리합니다 (다음에 엉뚱한 코스가 되살아나지 않게).
     */
    private suspend fun watchRecordingAndSave() {
        var lastSaved: SavedFollow? = null
        var wasRecording = false

        // 기록이 켜지면(이어서 기록 포함) 지금 따라 걷는 코스를 바로 저장, 꺼지면 정리
        viewModelScope.launch {
            recordingController.liveState.map { it.isRecording || it.isPaused }.distinctUntilChanged().collect { active ->
                if (active) {
                    wasRecording = true
                    val f = _follow.value
                    // 서비스가 새 기록 행을 DB 에 만들 시간을 조금 준 뒤 기록 id 확인
                    // (방금 시작한 기록을 예전 '미완료 기록'으로 잘못 보지 않게)
                    if (f != null) delay(1_500)
                    // 기록 id 에 묶어 저장: 미완료 기록을 두고 '새로 기록'을 시작해 id 가 달라지면
                    // 예전 코스는 여기서 바로 정리되어 되살아나지 않음
                    val activeId = activities.getActiveRecording()?.id
                    if (f != null && FollowRestorePolicy.onRecordingStart(f.boundActivityId, activeId) == OnRecordingStart.CLEAR) {
                        lastSaved = null
                        endFollowAfterRecording()   // 디스크에서 먼저 지우고 카드를 닫음
                        return@collect
                    }
                    f?.let { cur ->
                        val along = (cur.follower.lastAlongM ?: 0.0)
                        val next = SavedFollow(cur.id, along, cur.follower.lastOffsetM ?: 0.0, cur.follower.isOffCourse, cur.sessionId, activeId, cur.reversed)
                        lastSaved = next
                        _follow.value = cur.copy(boundActivityId = activeId)
                        followPrefs.saveNow(next)
                    }
                    return@collect
                }
                if (!wasRecording) return@collect
                // 기록이 꺼진 순간: 서비스가 마지막 상태를 DB 에 쓸 시간을 주고 확인 (최대 20초)
                var stillActive = true
                var waited = 0
                while (waited < 20_000) {
                    delay(1_000); waited += 1_000
                    stillActive = activities.getActiveRecording() != null ||
                        recordingController.liveState.value.let { st -> st.isRecording || st.isPaused }
                    if (!stillActive) break
                }
                if (!stillActive && _follow.value != null) {
                    wasRecording = false
                    endFollowAfterRecording()
                }
            }
        }

        // 걷는 동안: 25m 이상 진행했거나 이탈 상태가 바뀌면 저장
        follow.collect { f ->
            val cur = _follow.value ?: run { lastSaved = null; return@collect }
            if (f == null) return@collect
            if (!recordingController.liveState.value.let { it.isRecording || it.isPaused }) return@collect
            val along = (f.totalM - f.remainingM).coerceAtLeast(0.0)
            val prev = lastSaved
            val sameSession = prev != null && prev.sessionId == cur.sessionId && prev.reversed == cur.reversed
            if (sameSession && abs(prev!!.alongM - along) < 25.0 && prev.offCourse == f.isOffCourse) return@collect
            val next = SavedFollow(cur.id, along, f.offsetM ?: 0.0, f.isOffCourse, cur.sessionId, cur.boundActivityId, cur.reversed)
            lastSaved = next
            followPrefs.saveProgress(next)
        }
    }

    /**
     * 따라가기 카드 값: 남은 거리와 이탈 여부, 반대 방향 배지만 계산합니다.
     * 위치가 바뀔 때만(1초 타이머 갱신은 무시) 백그라운드에서 코스 전체와 비교합니다.
     */
    val follow: StateFlow<CourseFollowUi?> = combine(
        _follow,
        recordingController.liveState.map { it.currentLocation }.distinctUntilChanged()
    ) { f, loc ->
        f ?: return@combine null
        if (loc == null) {
            // 아직 위치 신호가 없을 때: 저장해 둔 진행 지점이 있으면 거기서부터 보여 줌
            val along = f.follower.lastAlongM
            val remaining = if (along != null) (f.follower.totalM - along).coerceAtLeast(0.0) else f.follower.totalM
            return@combine CourseFollowUi(f.name, f.route, f.follower.totalM, remaining, f.follower.isOffCourse, f.follower.lastOffsetM, f.reversed)
        }
        val (p, newlyOff) = f.follower.update(loc.latitude, loc.longitude)
        if (newlyOff) vibrateOffCourse()
        CourseFollowUi(f.name, f.route, f.follower.totalM, p.remainingM, f.follower.isOffCourse, p.offsetM, f.reversed)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // follow(위 상태 흐름)가 만들어진 뒤에 감시를 시작
        viewModelScope.launch { watchRecordingAndSave() }
    }

    private fun vibrateOffCourse() {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") (appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        runCatching { v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1)) }
    }
}

internal fun CourseEntity.toListItem(): CourseListItem {
    val pts = points()
    val stride = maxOf(1, pts.size / 80)
    return CourseListItem(
        id, name, distanceM, ascentM, GpxRuggedness.gradeFor(score),
        pts.filterIndexed { i, _ -> i % stride == 0 || i == pts.lastIndex }.map { LatLonPoint(it.lat, it.lon) }
    )
}
