package kr.co.nuni.gildam.data.course

import kr.co.nuni.gildam.data.gpxcompare.GpxPoint
import kr.co.nuni.gildam.data.gpxcompare.GpxRuggedness
import kotlin.math.abs

/**
 * 코스 지도 위치 ↔ 고도 그래프 거리(km) 연결.
 *
 * 코스 점들 사이 누적 거리(하버사인, [GpxRuggedness] 분석과 같은 방식)를 미리 계산해 두고,
 * 거리(km) → 경로 위 좌표(선분 사이는 선형 보간), 좌표 → 코스 시작부터의 거리 를 구합니다.
 * 순수 Kotlin 이라 단위 테스트로 확인합니다.
 */
class CourseRouteProfile(route: List<GpxPoint>) {
    private val pts = route
    /** cum[i] = 코스 시작점부터 i번째 점까지 거리(m) */
    private val cum = DoubleArray(route.size).also { c ->
        for (i in 1 until route.size) c[i] = c[i - 1] + GpxRuggedness.haversineM(route[i - 1], route[i])
    }

    val isEmpty: Boolean get() = pts.size < 2
    /** 코스 전체 길이(m) */
    val totalM: Double get() = cum.lastOrNull() ?: 0.0

    /** distanceKm 위치의 코스 좌표. 범위 밖이면 양 끝으로 붙임. */
    fun pointAt(distanceKm: Double): GpxPoint? {
        if (pts.isEmpty()) return null
        if (pts.size == 1) return pts[0]
        val target = (distanceKm.coerceAtLeast(0.0) * 1000.0).coerceAtMost(totalM)
        var i = 1
        while (i < cum.size && cum[i] < target) i++
        if (i >= cum.size) return pts.last()
        val segLen = cum[i] - cum[i - 1]
        val t = if (segLen <= 0.0) 0.0 else ((target - cum[i - 1]) / segLen).coerceIn(0.0, 1.0)
        val a = pts[i - 1]; val b = pts[i]
        return GpxPoint(
            lat = a.lat + (b.lat - a.lat) * t,
            lon = a.lon + (b.lon - a.lon) * t,
            ele = when {
                a.ele != null && b.ele != null -> a.ele + (b.ele - a.ele) * t
                else -> a.ele ?: b.ele
            }
        )
    }

    /** 코스 점 [index] 의 시작점부터 거리(m) */
    fun distanceMAt(index: Int): Double? = cum.getOrNull(index)

    /**
     * 화면·지도 좌표 [lat],[lon] 에 가장 가까운 '코스 위' 지점의 시작점부터 거리(km).
     * 선분 위로 투영해 점 사이에서도 정확히 잡습니다. [maxDistanceM] 보다 멀면 null.
     */
    fun nearestDistanceKm(lat: Double, lon: Double, maxDistanceM: Double = DEFAULT_TOLERANCE_M): Double? {
        if (pts.isEmpty()) return null
        if (pts.size == 1) {
            val d = GpxRuggedness.haversineM(pts[0], GpxPoint(lat, lon, null))
            return if (d <= maxDistanceM) 0.0 else null
        }
        val mPerDegLat = 111_320.0
        val mPerDegLon = 111_320.0 * kotlin.math.cos(Math.toRadians(lat)).coerceAtLeast(1e-6)
        var best = Double.MAX_VALUE
        var bestAlongM = 0.0
        for (i in 1 until pts.size) {
            val a = pts[i - 1]; val b = pts[i]
            val ax = (a.lon - lon) * mPerDegLon; val ay = (a.lat - lat) * mPerDegLat
            val bx = (b.lon - lon) * mPerDegLon; val by = (b.lat - lat) * mPerDegLat
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 <= 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val px = ax + t * dx; val py = ay + t * dy
            val d = kotlin.math.sqrt(px * px + py * py)
            if (d < best) { best = d; bestAlongM = cum[i - 1] + t * (cum[i] - cum[i - 1]) }
        }
        return if (best <= maxDistanceM) bestAlongM / 1000.0 else null
    }

    companion object {
        /** 지도 탭을 코스에 붙일 최대 거리 (m). 손가락으로 선을 정확히 못 눌러도 이 안이면 인정 */
        const val DEFAULT_TOLERANCE_M = 120.0
    }
}

/**
 * 고도 그래프 탭 x 좌표(px) → 코스 거리(km). 그래프 안쪽 영역 기준으로 계산합니다 (순수 함수).
 * 그래프 왼쪽 여백([leftPx])과 플롯 폭([plotWidthPx]) 밖은 양 끝으로 붙입니다.
 */
object ElevationChartHit {
    fun distanceKmForTap(
        tapXPx: Float,
        leftPx: Float,
        plotWidthPx: Float,
        maxKm: Double
    ): Double {
        if (plotWidthPx <= 0f || maxKm <= 0.0) return 0.0
        val ratio = ((tapXPx - leftPx) / plotWidthPx).coerceIn(0f, 1f)
        return ratio.toDouble() * maxKm
    }

    /** 거리(km)에 해당하는 그래프 점의 y(고도)를 프로파일에서 찾습니다 (선형 보간) */
    fun elevationAt(profile: List<kr.co.nuni.gildam.data.gpxcompare.ProfilePoint>, distanceKm: Double): Double? {
        if (profile.isEmpty()) return null
        if (distanceKm <= profile.first().distanceKm) return profile.first().elevationM
        if (distanceKm >= profile.last().distanceKm) return profile.last().elevationM
        for (i in 1 until profile.size) {
            val a = profile[i - 1]; val b = profile[i]
            if (distanceKm <= b.distanceKm) {
                val span = b.distanceKm - a.distanceKm
                if (span <= 0.0) return b.elevationM
                val t = (distanceKm - a.distanceKm) / span
                return a.elevationM + (b.elevationM - a.elevationM) * t
            }
        }
        return profile.last().elevationM
    }

    /** 선택 지점까지 중 가장 가까운 프로파일 인덱스 (없으면 -1) */
    fun nearestIndex(profile: List<kr.co.nuni.gildam.data.gpxcompare.ProfilePoint>, distanceKm: Double): Int {
        if (profile.isEmpty()) return -1
        var best = 0; var bestD = Double.MAX_VALUE
        profile.forEachIndexed { i, p ->
            val d = abs(p.distanceKm - distanceKm)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }
}
