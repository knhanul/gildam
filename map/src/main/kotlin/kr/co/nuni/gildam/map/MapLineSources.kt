package kr.co.nuni.gildam.map

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.co.nuni.gildam.recording.LatLonPoint

/** 선(경로·코스) 데이터가 바뀌었는지 싸게 비교하는 요약 (점 수 + 첫·끝 점) */
internal data class PolylineKey(val size: Int, val first: LatLonPoint?, val last: LatLonPoint?) {
    companion object {
        val EMPTY = PolylineKey(0, null, null)
        fun of(points: List<LatLonPoint>): PolylineKey =
            if (points.isEmpty()) EMPTY else PolylineKey(points.size, points.first(), points.last())
    }
}

/**
 * 지도 선 소스 3개(내 경로 · 남은 코스 · 지나온 코스)의 입력 요약.
 * [progress] 는 따라 걷는 코스가 있을 때만 현재 위치 (코스를 지나온/남은 부분으로 자르는 기준).
 */
internal data class LineSourceKey(val track: PolylineKey, val course: PolylineKey, val progress: LatLonPoint?) {
    companion object {
        fun of(track: List<LatLonPoint>, course: List<LatLonPoint>, current: LatLonPoint?) =
            LineSourceKey(PolylineKey.of(track), PolylineKey.of(course), current.takeIf { course.size >= 2 })
    }
}

/**
 * 선 소스 갱신 판단 (순수 Kotlin, 테스트용으로 분리).
 * - 같은 입력이면 건너뜀
 * - 처음·다른 경로(첫 점이 바뀜)·경로가 생기거나 비워짐·코스가 바뀜 → 바로
 * - 그 밖에 점이 늘어나는 것은 [intervalMs] 에 한 번으로 묶음 (남은 시간 뒤에 다시 확인)
 */
internal class LineSourceScheduler(private val intervalMs: Long = DEFAULT_INTERVAL_MS) {
    sealed interface Decision {
        data object Skip : Decision
        data object Now : Decision
        data class Later(val delayMs: Long) : Decision
    }

    var appliedKey: LineSourceKey? = null
        private set
    private var lastAppliedAtMs: Long? = null

    fun decide(key: LineSourceKey, nowMs: Long): Decision {
        val prev = appliedKey
        if (prev == key) return Decision.Skip
        val last = lastAppliedAtMs
        if (prev == null || last == null || isUrgent(prev, key)) return Decision.Now
        val elapsed = nowMs - last
        return if (elapsed >= intervalMs) Decision.Now else Decision.Later(intervalMs - elapsed)
    }

    fun markApplied(key: LineSourceKey, nowMs: Long) {
        appliedKey = key
        lastAppliedAtMs = nowMs
    }

    /** 스타일을 새로 올려 소스가 비었을 때: 다음 요청은 바로 반영 */
    fun invalidate() {
        appliedKey = null
        lastAppliedAtMs = null
    }

    private fun isUrgent(prev: LineSourceKey, next: LineSourceKey): Boolean =
        prev.course != next.course ||
            prev.track.first != next.track.first ||
            (prev.track.size == 0) != (next.track.size == 0)

    companion object {
        /** 경로 선은 최대 2초에 한 번만 다시 올림 (현재 위치 점·화살표·카메라는 따로 자주 갱신) */
        const val DEFAULT_INTERVAL_MS = 2_000L
    }
}

/** 백그라운드에서 만든 선 소스 내용. null 이면 그 소스는 그대로 둠 */
internal class BuiltLineSources(
    val route: String?,
    val remainingCourse: String?,
    val walkedCourse: String?,
    val splitIndex: Int
)

/** 지도 선 GeoJSON 만들기 (순수 Kotlin) */
internal object MapGeoJson {
    const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

    /** 구간 경계에서 끊어 MultiLineString 으로 (2점 미만 구간은 뺌) */
    fun lineString(points: List<LatLonPoint>): String {
        val segments = splitIntoSegments(points)
        if (segments.isEmpty()) return EMPTY
        val sb = StringBuilder(64 + points.size * 40)
        sb.append("""{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"MultiLineString","coordinates":[""")
        segments.forEachIndexed { si, seg ->
            if (si > 0) sb.append(',')
            sb.append('[')
            seg.forEachIndexed { i, p ->
                if (i > 0) sb.append(',')
                sb.append('[').append(p.longitude).append(',').append(p.latitude).append(']')
            }
            sb.append(']')
        }
        sb.append("]}}]}")
        return sb.toString()
    }

    /**
     * 소스 3개 내용 만들기. [previous] 와 비교해 바뀐 것만 만듦.
     * 코스는 [MapRouteGeometry.splitIndexAt] 으로 자른 번호가 같으면 다시 만들지 않음.
     */
    fun build(
        track: List<LatLonPoint>,
        course: List<LatLonPoint>,
        current: LatLonPoint?,
        previous: LineSourceKey?,
        previousSplitIndex: Int
    ): BuiltLineSources {
        val key = LineSourceKey.of(track, course, current)
        val route = if (previous?.track != key.track) lineString(track) else null
        val idx = if (course.size >= 2) MapRouteGeometry.splitIndexAt(course, current) else 0
        val courseChanged = previous?.course != key.course || idx != previousSplitIndex
        if (!courseChanged) return BuiltLineSources(route, null, null, idx)
        val walked = if (idx == 0) emptyList() else course.subList(0, idx + 1)
        val remaining = if (course.size >= 2 && idx == course.lastIndex) emptyList() else course.subList(minOf(idx, course.size), course.size)
        return BuiltLineSources(route, lineString(remaining), lineString(walked), idx)
    }
}

/** 구간 경계(isPauseBoundary)에서 나눈 구간들 (2점 미만 구간은 뺌) */
internal fun splitIntoSegments(points: List<LatLonPoint>): List<List<LatLonPoint>> {
    if (points.isEmpty()) return emptyList()
    val segments = mutableListOf<MutableList<LatLonPoint>>()
    var current = mutableListOf<LatLonPoint>()
    for (pt in points) {
        if (pt.isPauseBoundary && current.isNotEmpty()) {
            segments.add(current)
            current = mutableListOf()
        }
        current.add(pt)
    }
    if (current.isNotEmpty()) {
        segments.add(current)
    }
    return segments.filter { it.size >= 2 }
}

/** 선 소스에 실제로 넣는 쪽 (지도 객체 의존). 테스트에서는 가짜로 바꿔 끼움 */
internal interface LineSourceSink {
    /** 지금 스타일이 준비되어 있으면 true */
    fun isReady(): Boolean
    fun apply(built: BuiltLineSources)
}

/**
 * 지도 선 갱신 담당: "바뀐 것만, 가끔, 백그라운드에서".
 * - [submit] 은 매 재구성마다 불러도 됨 (메인 스레드에서는 요약 비교만)
 * - GeoJSON 문자열 만들기·코스 자르기는 [Dispatchers.Default], 소스 넣기만 메인([scope])에서
 */
internal class LineSourceUpdater(
    private val scope: CoroutineScope,
    private val sink: LineSourceSink,
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val scheduler: LineSourceScheduler = LineSourceScheduler(),
    private val buildDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default
) {
    private class Inputs(val track: List<LatLonPoint>, val course: List<LatLonPoint>, val current: LatLonPoint?) {
        val key = LineSourceKey.of(track, course, current)
    }

    private var latest: Inputs? = null
    private var buildJob: Job? = null
    private var wakeJob: Job? = null
    private var appliedSplitIndex = -1
    private var generation = 0

    fun submit(track: List<LatLonPoint>, course: List<LatLonPoint>, current: LatLonPoint?) {
        latest = Inputs(track, course, current)
        pump()
    }

    /** 스타일을 새로 올려 소스를 빈 값으로 만든 직후 */
    fun invalidate() {
        generation++
        scheduler.invalidate()
        appliedSplitIndex = -1
        pump()
    }

    private fun pump() {
        if (buildJob?.isActive == true) return // 끝나면 다시 확인함
        val inputs = latest ?: return
        if (!sink.isReady()) return
        when (val d = scheduler.decide(inputs.key, clock())) {
            LineSourceScheduler.Decision.Skip -> Unit
            is LineSourceScheduler.Decision.Later -> {
                if (wakeJob?.isActive != true) wakeJob = scope.launch { delay(d.delayMs); pump() }
            }
            LineSourceScheduler.Decision.Now -> {
                wakeJob?.cancel()
                val gen = generation
                val prevKey = scheduler.appliedKey
                val prevSplit = appliedSplitIndex
                buildJob = scope.launch {
                    val built = try {
                        withContext(buildDispatcher) {
                            MapGeoJson.build(inputs.track, inputs.course, inputs.current, prevKey, prevSplit)
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null // 이번 갱신만 건너뜀 (다음 요청에서 다시 시도)
                    }
                    if (built != null && gen == generation && sink.isReady()) {
                        sink.apply(built)
                        appliedSplitIndex = built.splitIndex
                        scheduler.markApplied(inputs.key, clock())
                    }
                    buildJob = null
                    if (built != null) pump()
                }
            }
        }
    }
}
