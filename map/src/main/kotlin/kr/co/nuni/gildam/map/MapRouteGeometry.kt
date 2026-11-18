package kr.co.nuni.gildam.map

import kr.co.nuni.gildam.data.course.CourseRouteProfile
import kr.co.nuni.gildam.data.gpxcompare.GpxPoint
import kr.co.nuni.gildam.recording.LatLonPoint
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * 코스 선 위 계산 (지도 표시용).
 * - [splitCourseAt]: 지나온 부분 / 남은 부분으로 자르기
 * - [pointAtKm] · [nearestCourseDistanceKm]: 고도 그래프 거리(km) ↔ 코스 좌표
 *   (거리 계산은 data 의 [CourseRouteProfile] 과 같은 규칙 — 한 곳에서만 구현)
 */
object MapRouteGeometry {
    data class Split(val walked: List<LatLonPoint>, val remaining: List<LatLonPoint>)

    /** 코스 위 탭을 인정하는 최대 거리 (m). 지도가 작으니 손가락 오차를 넉넉히 */
    const val TAP_TOLERANCE_M = CourseRouteProfile.DEFAULT_TOLERANCE_M

    /** 코스 자르기에 쓰는 최대 거리 (m) */
    const val MAX_SNAP_M = 1_500.0

    fun splitCourseAt(course: List<LatLonPoint>, current: LatLonPoint?): Split? {
        if (course.size < 2 || current == null) return null
        val idx = splitIndexAt(course, current)
        if (idx == 0) return Split(emptyList(), course)
        if (idx == course.lastIndex) return Split(course, emptyList())
        return Split(course.subList(0, idx + 1).toList(), course.subList(idx, course.size).toList())
    }

    /**
     * 지나온 부분의 마지막 점 번호 (지나온 코스 = 0..번호, 남은 코스 = 번호..끝).
     * 0 이면 아직 지나온 부분 없음 (위치가 없거나 1.5km 넘게 떨어진 경우 포함).
     */
    fun splitIndexAt(course: List<LatLonPoint>, current: LatLonPoint?): Int {
        if (course.size < 2 || current == null) return 0
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
        if (sqrt(best) * 111_320.0 > MAX_SNAP_M) return 0
        return bestIdx
    }

    /** 거리(km) 위치의 코스 좌표 (범위 밖은 양 끝) */
    fun pointAtKm(course: List<LatLonPoint>, km: Double): LatLonPoint? {
        val p = CourseRouteProfile(course.map { GpxPoint(it.latitude, it.longitude, null) }).pointAt(km) ?: return null
        return LatLonPoint(p.lat, p.lon)
    }

    /** 탭 좌표 → 코스 시작점부터 거리(km). 코스에서 [maxDistanceM] 보다 멀면 null */
    fun nearestCourseDistanceKm(
        course: List<LatLonPoint>,
        lat: Double,
        lon: Double,
        maxDistanceM: Double = TAP_TOLERANCE_M
    ): Double? = CourseRouteProfile(course.map { GpxPoint(it.latitude, it.longitude, null) })
        .nearestDistanceKm(lat, lon, maxDistanceM)
}
