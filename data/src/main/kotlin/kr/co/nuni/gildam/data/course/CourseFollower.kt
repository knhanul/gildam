package kr.co.nuni.gildam.data.course

import kr.co.nuni.gildam.data.gpxcompare.GpxPoint
import kr.co.nuni.gildam.data.gpxcompare.GpxRuggedness
import kotlin.math.cos
import kotlin.math.sqrt

data class CourseProgress(
    /** 경로선까지 최단 거리(m) */
    val offsetM: Double,
    /** 경로 시작점부터 가장 가까운 지점까지 거리(m) */
    val alongM: Double,
    val remainingM: Double
)

/**
 * 코스 따라 걷기: 현재 위치가 경로선에서 얼마나 벗어났는지, 남은 거리는 얼마인지 계산.
 * 각 선분에 대해 현재 위치 기준 국지 평면(등장방형) 투영 후 점-선분 최단 거리를 구합니다.
 * 이탈은 [OFF_COURSE_M] 초과, 복귀는 [BACK_ON_COURSE_M] 미만 (깜빡임 방지 히스테리시스).
 */
class CourseFollower(private val route: List<GpxPoint>) {
    private val cum = DoubleArray(route.size).also { c ->
        for (i in 1 until route.size) c[i] = c[i - 1] + GpxRuggedness.haversineM(route[i - 1], route[i])
    }
    val totalM: Double get() = cum.lastOrNull() ?: 0.0

    var isOffCourse: Boolean = false
        private set

    /** 마지막으로 확인한 위치 기준 진행(m) · 이탈(m). 아직 위치를 못 받았으면 null */
    var lastAlongM: Double? = null
        private set
    var lastOffsetM: Double? = null
        private set

    /** 저장해 두었던 진행 상태로 되돌립니다 (앱이 백그라운드에서 정리됐다 돌아왔을 때). */
    fun restoreState(alongM: Double?, offsetM: Double?, offCourse: Boolean) {
        lastAlongM = alongM
        lastOffsetM = offsetM
        isOffCourse = offCourse
    }

    fun locate(lat: Double, lon: Double): CourseProgress {
        if (route.isEmpty()) return CourseProgress(Double.MAX_VALUE, 0.0, 0.0)
        if (route.size == 1) {
            val d = GpxRuggedness.haversineM(route[0], GpxPoint(lat, lon, null)); return CourseProgress(d, 0.0, 0.0)
        }
        val mPerDegLat = 111_320.0
        val mPerDegLon = 111_320.0 * cos(Math.toRadians(lat))
        var best = Double.MAX_VALUE; var bestAlong = 0.0
        for (i in 1 until route.size) {
            val a = route[i - 1]; val b = route[i]
            val ax = (a.lon - lon) * mPerDegLon; val ay = (a.lat - lat) * mPerDegLat
            val bx = (b.lon - lon) * mPerDegLon; val by = (b.lat - lat) * mPerDegLat
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 <= 0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val px = ax + t * dx; val py = ay + t * dy
            val d = sqrt(px * px + py * py)
            if (d < best) { best = d; bestAlong = cum[i - 1] + t * (cum[i] - cum[i - 1]) }
        }
        return CourseProgress(best, bestAlong, (totalM - bestAlong).coerceAtLeast(0.0))
    }

    /** 위치를 반영하고 이탈 상태가 '새로' 켜졌으면 true (진동 트리거용). */
    fun update(lat: Double, lon: Double): Pair<CourseProgress, Boolean> {
        val p = locate(lat, lon)
        lastAlongM = p.alongM
        lastOffsetM = p.offsetM
        val was = isOffCourse
        isOffCourse = if (was) p.offsetM >= BACK_ON_COURSE_M else p.offsetM > OFF_COURSE_M
        return p to (!was && isOffCourse)
    }

    companion object {
        const val OFF_COURSE_M = 50.0
        const val BACK_ON_COURSE_M = 35.0
    }
}
