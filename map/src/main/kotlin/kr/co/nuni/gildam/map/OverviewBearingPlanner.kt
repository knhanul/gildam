package kr.co.nuni.gildam.map

import kr.co.nuni.gildam.recording.LatLonPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** 회전된 전체 경로 보기 카메라 (중심·줌·회전) */
data class RotatedOverviewCamera(val latitude: Double, val longitude: Double, val zoom: Double, val bearing: Double)

/**
 * 전체 경로 보기에서 '세로 화면을 가장 잘 채우는' 지도 회전각 고르기 (순수 함수, 단위 테스트 가능).
 * - 경로 점들의 주축(가장 길게 퍼진 방향)을 구해 그 축이 화면 세로가 되게 돌립니다.
 * - 거의 남북이거나 뚜렷한 긴 방향이 없으면 0°(북쪽 위).
 * - 움직이는 동안 계속 돌지 않게, 바뀐 각이 [HYSTERESIS_DEG] 보다 클 때만 새 각을 씁니다.
 */
object OverviewBearingPlanner {
    /** 이보다 작게 기울면 그냥 북쪽 위 */
    const val SNAP_TO_NORTH_DEG = 20.0
    /** 긴 축/짧은 축 퍼짐 비(분산비)가 이보다 작으면 돌리지 않음 */
    const val MIN_ELONGATION = 2.0
    /** 이보다 크게 바뀔 때만 다시 돌림 */
    const val HYSTERESIS_DEG = 28.0

    private const val M_PER_DEG = 111_320.0

    /** 지도 회전각(-90 < b ≤ 90). 0 = 북쪽 위, 90 = 동쪽이 위(동서로 긴 경로가 세로로) */
    fun bestBearing(points: List<LatLonPoint>): Double {
        if (points.size < 2) return 0.0
        val lat0 = points.sumOf { it.latitude } / points.size
        val lon0 = points.sumOf { it.longitude } / points.size
        val k = cos(Math.toRadians(lat0))
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (p in points) {
            val x = (p.longitude - lon0) * k * M_PER_DEG // 동
            val y = (p.latitude - lat0) * M_PER_DEG      // 북
            sxx += x * x; syy += y * y; sxy += x * y
        }
        val tr = sxx + syy
        if (tr <= 1e-6) return 0.0
        val det = sxx * syy - sxy * sxy
        val disc = sqrt(max(0.0, tr * tr / 4 - det))
        val l1 = tr / 2 + disc; val l2 = tr / 2 - disc
        if (l2 > 1e-9 && l1 / l2 < MIN_ELONGATION) return 0.0
        // 주축 방위(북에서 시계방향): 벡터 (x=동, y=북) 의 방위 = atan2(x, y)
        val ang = 0.5 * atan2(2 * sxy, syy - sxx) // 북 기준 시계방향(라디안) 의 주축
        var b = Math.toDegrees(ang)
        b = normalizeAxis(b)
        return if (abs(b) < SNAP_TO_NORTH_DEG) 0.0 else b
    }

    /** 축 방향(180° 주기)을 -90 < b ≤ 90 으로 */
    fun normalizeAxis(deg: Double): Double {
        var b = ((deg % 180.0) + 180.0) % 180.0 // 0..180
        if (b > 90.0) b -= 180.0
        return b
    }

    /** 두 축 사이 각 차이(0..90) */
    fun axisDiff(a: Double, b: Double): Double {
        val d = abs(normalizeAxis(a - b))
        return d
    }

    /** 히스테리시스: 지금 각에서 [HYSTERESIS_DEG] 넘게 달라질 때만 새 각 */
    fun choose(current: Double?, best: Double, threshold: Double = HYSTERESIS_DEG): Double =
        if (current == null || axisDiff(current, best) > threshold) best else current

    /**
     * 축만 정해진 회전각(±90 안)에 방향을 붙임: 지나온 길이 아래→위로 이어지도록(출발점 아래, 현재 위치 위).
     * 북쪽 위(0°)는 그대로 둡니다.
     */
    fun orient(axisBearing: Double, points: List<LatLonPoint>): Double {
        if (axisBearing == 0.0 || points.size < 2) return axisBearing
        val a = points.first(); val b = points.last()
        val k = cos(Math.toRadians((a.latitude + b.latitude) / 2))
        val (_, up) = rotate((b.longitude - a.longitude) * k * M_PER_DEG, (b.latitude - a.latitude) * M_PER_DEG, axisBearing)
        val out = if (up < 0) axisBearing + 180.0 else axisBearing
        return ((out % 360.0) + 360.0) % 360.0
    }

    /** 지도 회전 [bearing] 에서 출발점이 현재 위치보다 화면 위쪽에 있는지 */
    fun startIsAtTop(start: LatLonPoint?, current: LatLonPoint?, bearing: Double): Boolean {
        if (start == null || current == null) return false
        val k = cos(Math.toRadians((start.latitude + current.latitude) / 2))
        val (_, up) = rotate((start.longitude - current.longitude) * k * M_PER_DEG, (start.latitude - current.latitude) * M_PER_DEG, bearing)
        return up > 0
    }

    /** 동(x)·북(y) m 좌표를 지도 회전 [bearing] 의 화면 좌표(오른쪽, 위)로 */
    fun rotate(xEast: Double, yNorth: Double, bearing: Double): Pair<Double, Double> {
        val t = Math.toRadians(bearing)
        val right = xEast * cos(t) - yNorth * sin(t)
        val up = xEast * sin(t) + yNorth * cos(t)
        return right to up
    }

    /**
     * 회전된 화면에서 경로가 여백 안쪽에 꼭 맞게 들어가는 카메라.
     * MapLibre 줌 z 에서 1dp 당 m = 78271.517 × cos(위도) / 2^z (512px 타일), px → dp 는 [density] 로 나눔.
     */
    fun fitRotated(
        points: List<LatLonPoint>,
        bearing: Double,
        viewW: Int,
        viewH: Int,
        pad: OverlayPadding,
        density: Float,
        maxZoom: Double = 17.0,
        minSpanM: Double = 150.0
    ): RotatedOverviewCamera? {
        if (points.isEmpty() || viewW <= 0 || viewH <= 0) return null
        val lat0 = points.sumOf { it.latitude } / points.size
        val lon0 = points.sumOf { it.longitude } / points.size
        val k = cos(Math.toRadians(lat0)).coerceAtLeast(1e-6)
        var minR = Double.MAX_VALUE; var maxR = -Double.MAX_VALUE; var minU = Double.MAX_VALUE; var maxU = -Double.MAX_VALUE
        for (p in points) {
            val (r, u) = rotate((p.longitude - lon0) * k * M_PER_DEG, (p.latitude - lat0) * M_PER_DEG, bearing)
            minR = minOf(minR, r); maxR = maxOf(maxR, r); minU = minOf(minU, u); maxU = maxOf(maxU, u)
        }
        val spanR = max(maxR - minR, minSpanM); val spanU = max(maxU - minU, minSpanM)
        val availWdp = (viewW - pad.left - pad.right).coerceAtLeast(1) / density
        val availHdp = (viewH - pad.top - pad.bottom).coerceAtLeast(1) / density
        val mPerDp = max(spanR / availWdp, spanU / availHdp)
        val zoom = (ln(78_271.517 * k / mPerDp) / ln(2.0)).coerceAtMost(maxZoom)
        // 회전 좌표 중심 → 동·북 m → 위경도 (역회전)
        val cr = (minR + maxR) / 2; val cu = (minU + maxU) / 2
        val t = Math.toRadians(bearing)
        val x = cr * cos(t) + cu * sin(t)
        val y = -cr * sin(t) + cu * cos(t)
        return RotatedOverviewCamera(lat0 + y / M_PER_DEG, lon0 + x / (k * M_PER_DEG), zoom, ((bearing % 360) + 360) % 360)
    }
}
