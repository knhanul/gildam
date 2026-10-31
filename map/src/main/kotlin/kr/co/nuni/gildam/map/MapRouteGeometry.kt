package kr.co.nuni.gildam.map

import kr.co.nuni.gildam.recording.LatLonPoint
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * 코스 선을 '지나온 부분'과 '남은 부분'으로 나눕니다 (지도 표시용, 순수 함수).
 * 현재 위치에서 가장 가까운 코스 점을 기준으로 자르고, 그 점을 양쪽에 함께 넣어 선이 끊겨 보이지 않게 합니다.
 */
object MapRouteGeometry {
    data class Split(val walked: List<LatLonPoint>, val remaining: List<LatLonPoint>)

    fun splitCourseAt(course: List<LatLonPoint>, current: LatLonPoint?): Split? {
        if (course.size < 2 || current == null) return null
        val kx = cos(Math.toRadians(current.latitude))
        var bestIdx = 0
        var best = Double.MAX_VALUE
        course.forEachIndexed { i, p ->
            val dLat = p.latitude - current.latitude
            val dLon = (p.longitude - current.longitude) * kx
            val d = dLat * dLat + dLon * dLon
            if (d < best) { best = d; bestIdx = i }
        }
        // 너무 멀리 떨어져 있으면(1.5km 이상) 자르지 않음 (엉뚱한 곳에서 잘리는 것 방지)
        val degToM = 111_320.0
        if (sqrt(best) * degToM > MAX_SNAP_M) return Split(emptyList(), course)
        if (bestIdx == 0) return Split(emptyList(), course)
        if (bestIdx == course.lastIndex) return Split(course, emptyList())
        val walked = course.subList(0, bestIdx + 1).toList()
        val remaining = course.subList(bestIdx, course.size).toList()
        return Split(walked, remaining)
    }

    /** 코스에서 지금 위치까지 남은 거리(m) — 진행 점 표시·디버그용 */
    const val MAX_SNAP_M = 1_500.0
}
