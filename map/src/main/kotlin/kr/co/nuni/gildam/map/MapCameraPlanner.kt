package kr.co.nuni.gildam.map

import kr.co.nuni.gildam.recording.LatLonPoint
import kotlin.math.max
import kotlin.math.min

/** 기록 중 지도 카메라 모드 */
enum class MapCameraMode(val labelKo: String) {
    FOLLOW("내 위치 따라가기"),
    OVERVIEW("전체 경로");
    companion object { val DEFAULT = FOLLOW }
}

data class GeoBounds(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double) {
    val latSpan get() = maxLat - minLat
    val lonSpan get() = maxLon - minLon
    fun contains(p: LatLonPoint) = p.latitude in minLat..maxLat && p.longitude in minLon..maxLon
    fun contains(b: GeoBounds) = b.minLat >= minLat && b.maxLat <= maxLat && b.minLon >= minLon && b.maxLon <= maxLon
}

/** 지도 위 카드에 가려지는 영역(px). 카메라 맞춤 시 이만큼 비워 둠. */
data class OverlayPadding(val left: Int, val top: Int, val right: Int, val bottom: Int)

data class OverviewFit(val bounds: GeoBounds, val padding: OverlayPadding)

/** 카메라를 옮길 목표. null 이면 "지도를 움직이지 말라"(직전 화면 유지). */
data class CameraTarget(val latitude: Double, val longitude: Double, val zoom: Double)

/**
 * 카메라 계산 (순수 함수, MapLibre 의존 없음 → 단위 테스트 가능).
 * - 전체 경로: 출발~현재 위치(+따라 걷는 코스) 경계 + 여유, 카드 높이만큼 패딩
 * - 재맞춤: 위치가 지난번 경계의 안쪽 여유 밖으로 나가거나, 경계가 커졌고 최소 간격이 지났을 때
 * - 따라가기 줌: 빠를수록·멀리 갈수록 조금씩 넓게
 */
object MapCameraPlanner {
    /** 너무 작은 경계(정지 상태)는 이 정도(도)로 넓힘 ≈ 150m */
    const val MIN_SPAN_DEG = 0.0014
    /** 경계 바깥 여유 비율 (상하좌우 인위적 여백 없이 전체 지도 화면 활용) */
    const val MARGIN_FRACTION = 0.0
    /** 재맞춤 최소 간격 */
    const val MIN_REFIT_INTERVAL_MS = 4_000L
    const val EDGE_PX = 0

    fun targetFor(
        track: List<LatLonPoint>,
        current: LatLonPoint?,
        course: List<LatLonPoint>
    ): CameraTarget? =
        current?.let { CameraTarget(it.latitude, it.longitude, 15.2) }
            ?: track.lastOrNull()?.let { CameraTarget(it.latitude, it.longitude, 15.2) }
            ?: course.firstOrNull()?.let { CameraTarget(it.latitude, it.longitude, 15.2) }
            ?: null // 아무것도 없으면 움직이지 않음

    fun boundsOf(points: List<LatLonPoint>): GeoBounds? {
        if (points.isEmpty()) return null
        var a = points[0].latitude; var b = points[0].longitude; var c = a; var d = b
        for (p in points) { a = min(a, p.latitude); b = min(b, p.longitude); c = max(c, p.latitude); d = max(d, p.longitude) }
        return GeoBounds(a, b, c, d)
    }

    /** 비율만큼 바깥으로 넓히고, 최소 크기를 보장 */
    fun expand(b: GeoBounds, fraction: Double = MARGIN_FRACTION, minSpan: Double = MIN_SPAN_DEG): GeoBounds {
        if (fraction == 0.0 && b.latSpan >= minSpan && b.lonSpan >= minSpan) return b
        val latSpan = max(b.latSpan * (1 + 2 * fraction), minSpan)
        val lonSpan = max(b.lonSpan * (1 + 2 * fraction), minSpan)
        val cLat = (b.minLat + b.maxLat) / 2; val cLon = (b.minLon + b.maxLon) / 2
        val minLat = if (fraction == 0.0 && b.latSpan >= minSpan) b.minLat else cLat - latSpan / 2
        val maxLat = if (fraction == 0.0 && b.latSpan >= minSpan) b.maxLat else cLat + latSpan / 2
        val minLon = if (fraction == 0.0 && b.lonSpan >= minSpan) b.minLon else cLon - lonSpan / 2
        val maxLon = if (fraction == 0.0 && b.lonSpan >= minSpan) b.maxLon else cLon + lonSpan / 2
        return GeoBounds(minLat, minLon, maxLat, maxLon)
    }

    /** 카드 크기(px)로 패딩 계산. 화면의 절반 이상을 가리지 않도록 제한. */
    fun overlayPadding(viewW: Int, viewH: Int, topCardsPx: Int, bottomCardPx: Int, edgePx: Int = EDGE_PX): OverlayPadding {
        val maxV = (viewH * 0.8).toInt()
        var top = topCardsPx + edgePx; var bottom = bottomCardPx + edgePx
        if (top + bottom > maxV && top + bottom > 0) {
            val k = maxV.toDouble() / (top + bottom); top = (top * k).toInt(); bottom = (bottom * k).toInt()
        }
        val side = min(edgePx, viewW / 6)
        return OverlayPadding(side, top, side, bottom)
    }

    fun overviewFit(track: List<LatLonPoint>, current: LatLonPoint?, course: List<LatLonPoint>, padding: OverlayPadding): OverviewFit? {
        val all = ArrayList<LatLonPoint>(track.size + course.size + 1).apply { addAll(track); current?.let(::add); addAll(course) }
        val b = boundsOf(all) ?: return null
        return OverviewFit(expand(b), padding)
    }

    /** 다시 맞춰야 하는지 */
    fun needsRefit(lastFit: GeoBounds?, wanted: GeoBounds, current: LatLonPoint?, nowMs: Long, lastFitMs: Long, minIntervalMs: Long = MIN_REFIT_INTERVAL_MS): Boolean {
        if (lastFit == null) return true
        // 현재 위치가 지난 경계 밖으로 나가면 바로 재맞춤
        if (current != null && !lastFit.contains(current)) return true
        if (nowMs - lastFitMs < minIntervalMs) return false
        // 경로가 지난 경계를 넘었거나, 경계가 많이 작아져야 하는 경우(새 출발 등)
        if (!lastFit.contains(wanted)) return true
        return wanted.latSpan < lastFit.latSpan * 0.5 && wanted.lonSpan < lastFit.lonSpan * 0.5
    }

    /** 따라가기 줌: 걷기 ≈17, 자전거 ≈15.5, 차 ≈14. 오래 갈수록 최대 0.7 더 넓게. */
    fun followZoom(speedMps: Float, distanceM: Double): Double {
        val bySpeed = 17.0 - (speedMps.coerceAtLeast(0f) / 2.5).coerceAtMost(3.0)
        val byDistance = (distanceM / 10_000.0).coerceIn(0.0, 0.7)
        return (bySpeed - byDistance).coerceIn(13.5, 17.0)
    }
}
