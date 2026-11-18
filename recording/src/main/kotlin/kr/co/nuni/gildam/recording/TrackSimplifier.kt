package kr.co.nuni.gildam.recording

import kotlin.math.cos

/**
 * 지도에 그릴 경로 점 줄이기 (Douglas–Peucker). **화면용**이며 저장·거리 계산·동기화에 쓰는 원본 점은 건드리지 않습니다.
 * - 구간 경계([LatLonPoint.isPauseBoundary])마다 따로 줄여서 끊긴 구간이 이어지지 않게 하고, 각 구간의 시작·끝 점은 항상 남깁니다.
 * - 거리는 구간 첫 점 기준 국지 평면(등장방형)에서 점-선분 최단 거리로 잽니다.
 */
object TrackSimplifier {
    /** 화면용 허용 오차(m). 줌 17 에서 몇 dp 정도라 눈으로는 원본과 같아 보임 */
    const val DEFAULT_TOLERANCE_M = 3.0
    /** 화면에 넘기는 경로 점 수 상한 */
    const val MAX_DISPLAY_POINTS = 2_000
    private const val M_PER_DEG = 111_320.0
    private const val MAX_RELAX_STEPS = 24

    fun simplify(points: List<LatLonPoint>, toleranceM: Double = DEFAULT_TOLERANCE_M): List<LatLonPoint> {
        if (points.size <= 2) return points.toList()
        val out = ArrayList<LatLonPoint>(minOf(points.size, 512))
        var start = 0
        for (i in 1..points.size) {
            if (i == points.size || points[i].isPauseBoundary) {
                simplifySegment(points, start, i, toleranceM, out)
                start = i
            }
        }
        return out
    }

    /** 점 수가 [maxPoints] 이하가 될 때까지 허용 오차를 두 배씩 키워 줄임. (결과, 쓴 허용 오차) */
    fun simplifyToLimit(
        points: List<LatLonPoint>,
        toleranceM: Double = DEFAULT_TOLERANCE_M,
        maxPoints: Int = MAX_DISPLAY_POINTS
    ): Pair<List<LatLonPoint>, Double> {
        var tol = toleranceM
        var r = simplify(points, tol)
        var steps = 0
        while (r.size > maxPoints && steps++ < MAX_RELAX_STEPS) {
            tol *= 2
            r = simplify(r, tol)
        }
        return r to tol
    }

    /** points[from, to) 한 구간을 줄여 [out] 에 붙임 */
    private fun simplifySegment(points: List<LatLonPoint>, from: Int, to: Int, toleranceM: Double, out: MutableList<LatLonPoint>) {
        val n = to - from
        if (n <= 2) {
            for (k in from until to) out.add(points[k])
            return
        }
        val lat0 = points[from].latitude
        val lon0 = points[from].longitude
        val kx = cos(Math.toRadians(lat0)) * M_PER_DEG
        val xs = DoubleArray(n) { (points[from + it].longitude - lon0) * kx }
        val ys = DoubleArray(n) { (points[from + it].latitude - lat0) * M_PER_DEG }
        val keep = BooleanArray(n)
        keep[0] = true; keep[n - 1] = true
        val tol2 = toleranceM * toleranceM
        val stack = IntArray(2 * n)
        var sp = 0
        stack[sp++] = 0; stack[sp++] = n - 1
        while (sp > 0) {
            val b = stack[--sp]; val a = stack[--sp]
            if (b - a < 2) continue
            val ax = xs[a]; val ay = ys[a]
            val dx = xs[b] - ax; val dy = ys[b] - ay
            val len2 = dx * dx + dy * dy
            var maxD = -1.0; var idx = -1
            for (k in a + 1 until b) {
                val px = xs[k] - ax; val py = ys[k] - ay
                val t = if (len2 <= 0.0) 0.0 else ((px * dx + py * dy) / len2).coerceIn(0.0, 1.0)
                val ex = px - t * dx; val ey = py - t * dy
                val d = ex * ex + ey * ey
                if (d > maxD) { maxD = d; idx = k }
            }
            if (maxD > tol2) {
                keep[idx] = true
                stack[sp++] = a; stack[sp++] = idx
                stack[sp++] = idx; stack[sp++] = b
            }
        }
        for (k in 0 until n) if (keep[k]) out.add(points[from + k])
    }
}

/**
 * 기록 중 화면용 경로를 점이 들어올 때마다 조금씩 줄여 둡니다 (전체를 매번 다시 계산하지 않음).
 * - 앞부분(head): 이미 줄여서 확정한 점들
 * - 꼬리(tail): 새로 들어온 원본 점들. [chunkSize] 개가 모이면 줄여서 head 에 붙임 (마지막 점은 다음 꼬리의 시작으로 남김)
 * - 구간 경계 점이 오면 꼬리를 통째로 확정하고 새 구간을 시작 (끊김 유지)
 * - head 가 커지면 허용 오차를 두 배로 키워 head 만 다시 줄임 → 화면 점 수는 [maxPoints] 근처로 유지
 *
 * 메인 스레드(위치 콜백)에서 써도 되도록 한 번의 [add] 는 꼬리 길이만큼만 일합니다.
 */
class LiveTrackSimplifier(
    baseToleranceM: Double = TrackSimplifier.DEFAULT_TOLERANCE_M,
    private val maxPoints: Int = TrackSimplifier.MAX_DISPLAY_POINTS,
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE
) {
    init { require(maxPoints >= chunkSize * 2) { "maxPoints must be at least twice chunkSize" } }

    private val head = ArrayList<LatLonPoint>()
    private val tail = ArrayList<LatLonPoint>(chunkSize + 1)

    /** 지금까지 받은 원본 점 수 (화면 갱신 판단용 버전 번호로도 씀) */
    var rawCount: Int = 0
        private set

    /** 지금 쓰는 허용 오차(m). 경로가 길어지면 커짐 */
    var toleranceM: Double = baseToleranceM
        private set

    val displaySize: Int get() = head.size + tail.size

    fun add(p: LatLonPoint) {
        rawCount++
        if (p.isPauseBoundary && tail.isNotEmpty()) freezeTail(keepLast = false)
        tail.add(p)
        if (tail.size >= chunkSize) freezeTail(keepLast = true)
    }

    fun addAll(points: Iterable<LatLonPoint>) = points.forEach(::add)

    /** 화면에 넘길 경로 (새 리스트, 점 수는 [maxPoints] 근처로 제한됨) */
    fun snapshot(): List<LatLonPoint> = ArrayList<LatLonPoint>(head.size + tail.size).apply {
        addAll(head); addAll(tail)
    }

    private fun freezeTail(keepLast: Boolean) {
        val simplified = TrackSimplifier.simplify(tail, toleranceM)
        if (keepLast) {
            val last = tail.last()
            head.addAll(simplified.subList(0, simplified.size - 1))
            tail.clear()
            tail.add(last)
        } else {
            head.addAll(simplified)
            tail.clear()
        }
        var steps = 0
        while (head.size + chunkSize > maxPoints && steps++ < MAX_COMPACT_STEPS) {
            toleranceM *= 2
            val r = TrackSimplifier.simplify(head, toleranceM)
            head.clear()
            head.addAll(r)
        }
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE = 120
        private const val MAX_COMPACT_STEPS = 24
    }
}
