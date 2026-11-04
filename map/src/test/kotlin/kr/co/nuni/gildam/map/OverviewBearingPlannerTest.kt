package kr.co.nuni.gildam.map

import kr.co.nuni.gildam.recording.LatLonPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OverviewBearingPlannerTest {
    private fun line(dLat: Double, dLon: Double, n: Int = 30, wobble: Double = 0.0) =
        (0 until n).map { i -> LatLonPoint(37.5 + dLat * i + (if (i % 2 == 0) wobble else -wobble), 127.0 + dLon * i) }

    @Test fun eastWestTrackRotatesAbout90() {
        val b = OverviewBearingPlanner.bestBearing(line(0.0, 0.0005, wobble = 0.00002))
        assertEquals(90.0, abs(b), 3.0)
    }

    @Test fun northSouthTrackKeepsNorthUp() =
        assertEquals(0.0, OverviewBearingPlanner.bestBearing(line(0.0005, 0.0)), 1e-9)

    @Test fun slightTiltSnapsToNorth() {
        // 약 10° 기운 남북 경로 → 0
        val k = kotlin.math.cos(Math.toRadians(37.5))
        val dLon = 0.0005 * kotlin.math.tan(Math.toRadians(10.0)) / k
        assertEquals(0.0, OverviewBearingPlanner.bestBearing(line(0.0005, dLon)), 1e-9)
    }

    @Test fun diagonalNorthEastTrackRotates45() {
        val k = kotlin.math.cos(Math.toRadians(37.5))
        val b = OverviewBearingPlanner.bestBearing(line(0.0005, 0.0005 / k))
        assertEquals(45.0, b, 2.0)
        val nw = OverviewBearingPlanner.bestBearing(line(0.0005, -0.0005 / k))
        assertEquals(-45.0, nw, 2.0)
    }

    @Test fun roundTrackKeepsNorthUp() {
        val ring = (0 until 36).map { i ->
            val t = Math.toRadians(i * 10.0); LatLonPoint(37.5 + 0.003 * kotlin.math.sin(t), 127.0 + 0.0038 * kotlin.math.cos(t))
        }
        assertEquals(0.0, OverviewBearingPlanner.bestBearing(ring), 1e-9)
    }

    @Test fun hysteresisIgnoresSmallChanges() {
        assertEquals(60.0, OverviewBearingPlanner.choose(null, 60.0), 1e-9)
        assertEquals(60.0, OverviewBearingPlanner.choose(60.0, 80.0), 1e-9)   // 20° → 그대로
        assertEquals(0.0, OverviewBearingPlanner.choose(60.0, 0.0), 1e-9)     // 60° → 바꿈
        // 축은 180° 주기: 89° 와 -89° 는 2° 차이
        assertEquals(89.0, OverviewBearingPlanner.choose(89.0, -89.0), 1e-9)
    }

    @Test fun rotatedFitKeepsTrackInsidePaddedArea() {
        val pts = line(0.0, 0.0005)
        val pad = OverlayPadding(180, 400, 180, 1100)
        val cam = OverviewBearingPlanner.fitRotated(pts, 90.0, 1080, 2400, pad, density = 2.625f)!!
        assertEquals(90.0, cam.bearing, 1e-9)
        // 동서 1.3km 경로를 세로 (2400-1500)/2.625 ≈ 343dp 에 → 1dp ≈ 3.8m
        val mPerDp = 78_271.517 * kotlin.math.cos(Math.toRadians(37.5)) / Math.pow(2.0, cam.zoom)
        val spanM = 29 * 0.0005 * 111_320.0 * kotlin.math.cos(Math.toRadians(37.5))
        assertTrue(spanM / mPerDp <= (2400 - 1500) / 2.625 + 1)
        assertEquals(37.5, cam.latitude, 1e-4)
        assertEquals(127.0 + 14.5 * 0.0005, cam.longitude, 1e-4)
    }

    @Test fun orientPutsStartAtBottom() {
        val eastward = line(0.0, 0.0005)
        assertEquals(90.0, OverviewBearingPlanner.orient(90.0, eastward), 1e-9)
        assertEquals(90.0, OverviewBearingPlanner.orient(-90.0, eastward), 1e-9)
        assertEquals(270.0, OverviewBearingPlanner.orient(90.0, eastward.reversed()), 1e-9)
        assertEquals(0.0, OverviewBearingPlanner.orient(0.0, line(-0.0005, 0.0)), 1e-9)
    }

    @Test fun startSideAfterRotation() {
        val south = line(-0.0005, 0.0) // 남쪽으로 → 회전 없음 → S 위
        val b = OverviewBearingPlanner.orient(OverviewBearingPlanner.bestBearing(south), south)
        assertEquals(0.0, b, 1e-9)
        assertTrue(OverviewBearingPlanner.startIsAtTop(south.first(), south.last(), b))
        val north = line(0.0005, 0.0)
        assertTrue(!OverviewBearingPlanner.startIsAtTop(north.first(), north.last(), 0.0))
        val east = line(0.0, 0.0005) // 동서 → 회전해서 S 아래
        val be = OverviewBearingPlanner.orient(OverviewBearingPlanner.bestBearing(east), east)
        assertTrue(!OverviewBearingPlanner.startIsAtTop(east.first(), east.last(), be))
    }
}
