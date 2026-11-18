package kr.co.nuni.gildam.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class TrackSimplifierTest {

    private fun straight(n: Int, boundaryEvery: Int = 0): List<LatLonPoint> = (0 until n).map { i ->
        LatLonPoint(37.5 + i * 0.00001, 127.0 + i * 0.00001, isPauseBoundary = boundaryEvery > 0 && i > 0 && i % boundaryEvery == 0)
    }

    /** 약 1m 간격으로 나아가며 좌우로 ±20m 흔들리는 지그재그 (실제 GPS 처럼 약간의 잡음) */
    private fun zigzag(n: Int): List<LatLonPoint> = (0 until n).map { i ->
        val along = i * 1.0
        val side = 20.0 * sin(i / 15.0) + ((i * 7919) % 13 - 6) * 0.1
        LatLonPoint(37.5 + along / 111_320.0, 127.0 + side / (111_320.0 * cos(Math.toRadians(37.5))))
    }

    @Test
    fun straightLineKeepsOnlyEnds() {
        val pts = straight(10_000)
        val r = TrackSimplifier.simplify(pts)
        assertEquals(listOf(pts.first(), pts.last()), r)
    }

    @Test
    fun zigzagIsLimitedAndKeepsEnds() {
        val pts = zigzag(10_000)
        val (r, _) = TrackSimplifier.simplifyToLimit(pts)
        assertTrue("점 수 ${r.size}", r.size <= TrackSimplifier.MAX_DISPLAY_POINTS)
        assertEquals(pts.first(), r.first())
        assertEquals(pts.last(), r.last())
        // 원본 점이 줄인 선에서 허용 오차(+여유) 안에 있음
        assertTrue(maxDeviationM(pts, r) <= TrackSimplifier.DEFAULT_TOLERANCE_M * 4 + 0.01)
    }

    @Test
    fun pauseSegmentsArePreserved() {
        val pts = straight(10_000, boundaryEvery = 2_500)
        val r = TrackSimplifier.simplify(pts)
        assertEquals(segmentEnds(pts), segmentEnds(r))
        assertEquals(3, r.count { it.isPauseBoundary })
    }

    @Test
    fun incrementalStaysBoundedAndCloseToFull() {
        val pts = zigzag(10_000)
        val live = LiveTrackSimplifier()
        pts.forEach(live::add)
        val inc = live.snapshot()
        val full = TrackSimplifier.simplifyToLimit(pts).first
        assertEquals(10_000, live.rawCount)
        assertTrue("점 수 ${inc.size}", inc.size <= TrackSimplifier.MAX_DISPLAY_POINTS)
        assertEquals(pts.first(), inc.first())
        assertEquals(pts.last(), inc.last())
        // 증분 결과도 원본에서 크게 벗어나지 않고, 점 수도 한 번에 줄인 것과 비슷한 수준
        assertTrue("오차 ${maxDeviationM(pts, inc)}", maxDeviationM(pts, inc) <= live.toleranceM + 0.01)
        assertTrue("증분 ${inc.size} / 전체 ${full.size}", inc.size <= full.size * 3 + 50)
    }

    @Test
    fun incrementalKeepsSegmentBoundaries() {
        val pts = straight(5_000, boundaryEvery = 700)
        val live = LiveTrackSimplifier()
        pts.forEach(live::add)
        val r = live.snapshot()
        assertEquals(segmentEnds(pts), segmentEnds(r))
    }

    @Test
    fun twoHourTrackAddIsCheap() {
        // 1초 간격 2시간 분량: 점 하나 추가 + 화면용 스냅샷 만들기 (위치 콜백이 메인 스레드에서 하는 일)
        val pts = zigzag(7_200)
        val live = LiveTrackSimplifier()
        repeat(2) { warm -> if (warm == 0) zigzag(3_000).forEach { LiveTrackSimplifier().add(it) } }
        var worstNs = 0L
        var totalNs = 0L
        pts.forEach { p ->
            val t0 = System.nanoTime()
            live.add(p)
            live.snapshot()
            val dt = System.nanoTime() - t0
            totalNs += dt
            if (dt > worstNs) worstNs = dt
        }
        println("PERF live add+snapshot 7200pts: avg=%.3fms worst=%.3fms display=%d".format(totalNs / 7_200 / 1e6, worstNs / 1e6, live.displaySize))
        assertTrue(live.displaySize <= TrackSimplifier.MAX_DISPLAY_POINTS)
        assertTrue("worst ${worstNs / 1e6}ms", worstNs < 16_000_000L)
    }

    private fun segmentEnds(pts: List<LatLonPoint>): List<Pair<LatLonPoint, LatLonPoint>> {
        val out = mutableListOf<Pair<LatLonPoint, LatLonPoint>>()
        var start = 0
        for (i in 1..pts.size) if (i == pts.size || pts[i].isPauseBoundary) { out += pts[start] to pts[i - 1]; start = i }
        return out
    }

    /** 원본 각 점에서 줄인 선(같은 구간)까지 최단 거리의 최댓값(m) */
    private fun maxDeviationM(raw: List<LatLonPoint>, simplified: List<LatLonPoint>): Double {
        val kx = cos(Math.toRadians(37.5)) * 111_320.0
        fun xy(p: LatLonPoint) = (p.longitude - 127.0) * kx to (p.latitude - 37.5) * 111_320.0
        var worst = 0.0
        var j = 0
        for (p in raw) {
            val (px, py) = xy(p)
            var best = Double.MAX_VALUE
            // 줄인 선은 순서를 지키므로 앞쪽 몇 선분만 살펴봄
            val from = (j - 2).coerceAtLeast(0)
            for (k in from until (simplified.size - 1).coerceAtMost(j + 40)) {
                val (ax, ay) = xy(simplified[k]); val (bx, by) = xy(simplified[k + 1])
                val dx = bx - ax; val dy = by - ay
                val len2 = dx * dx + dy * dy
                val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
                val ex = px - ax - t * dx; val ey = py - ay - t * dy
                val d = sqrt(ex * ex + ey * ey)
                if (d < best) { best = d; j = k }
            }
            if (best > worst) worst = best
        }
        return worst
    }
}
