package kr.co.nuni.gildam.data.course

import kr.co.nuni.gildam.data.gpxcompare.GpxPoint
import kr.co.nuni.gildam.data.gpxcompare.GpxRuggedness
import kr.co.nuni.gildam.data.gpxcompare.ProfilePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HikingTimeEstimatorTest {
    @Test fun toblerFlatAndPeak() {
        assertEquals(5.036, HikingTimeEstimator.toblerKmh(0.0), 0.01)
        assertEquals(6.0, HikingTimeEstimator.toblerKmh(-0.05), 1e-9)
        assertTrue(HikingTimeEstimator.toblerKmh(0.3) < HikingTimeEstimator.toblerKmh(0.1))
        assertEquals(HikingTimeEstimator.MIN_KMH, HikingTimeEstimator.toblerKmh(2.0), 1e-9)
    }

    @Test fun flat5kmTakesAboutAnHour() {
        val prof = listOf(ProfilePoint(0.0, 100.0), ProfilePoint(5.0, 100.0))
        assertEquals(5.0 / 5.036 * 3600, HikingTimeEstimator.estimateSeconds(prof, 5000.0), 1.0)
    }

    @Test fun noProfileUsesFlatSpeed() {
        assertEquals(3600.0, HikingTimeEstimator.estimateSeconds(emptyList(), 5036.0), 1.0)
    }

    @Test fun climbIsSlowerThanFlatAndPaceScales() {
        val flat = listOf(ProfilePoint(0.0, 0.0), ProfilePoint(2.0, 0.0))
        val up = listOf(ProfilePoint(0.0, 0.0), ProfilePoint(2.0, 400.0)) // 20%
        val tFlat = HikingTimeEstimator.estimateSeconds(flat, 2000.0)
        val tUp = HikingTimeEstimator.estimateSeconds(up, 2000.0)
        assertTrue(tUp > tFlat * 1.5)
        assertEquals(tUp * 1.2, HikingTimeEstimator.estimateSeconds(up, 2000.0, paceFactor = 1.2), 1e-6)
    }

    @Test fun remainingFromMidpoint() {
        val prof = listOf(ProfilePoint(0.0, 0.0), ProfilePoint(4.0, 0.0))
        val full = HikingTimeEstimator.estimateSeconds(prof, 4000.0)
        assertEquals(full / 2, HikingTimeEstimator.estimateSeconds(prof, 4000.0, fromKm = 2.0), 1e-6)
    }

    @Test fun personalPaceIsClampedMedian() {
        assertNull(HikingTimeEstimator.personalPaceFactor(emptyList()))
        assertEquals(1.2, HikingTimeEstimator.personalPaceFactor(listOf(110.0 to 100.0, 120.0 to 100.0, 300.0 to 100.0))!!, 1e-9)
        assertEquals(HikingTimeEstimator.PACE_MIN, HikingTimeEstimator.personalPaceFactor(listOf(10.0 to 100.0))!!, 1e-9)
    }

    @Test fun formatting() {
        assertEquals("40분", HikingTimeEstimator.formatKo(40 * 60.0))
        assertEquals("2시간 15분", HikingTimeEstimator.formatKo(135 * 60.0))
        assertEquals("1시간", HikingTimeEstimator.formatKo(61 * 60.0))
    }
}

class CourseFollowerTest {
    // 동쪽으로 약 1km 직선 (위도 37.5)
    private val route = listOf(GpxPoint(37.5, 127.0, null), GpxPoint(37.5, 127.0113, null))
    private val mPerDegLat = 111_320.0

    @Test fun onRouteHasZeroOffset() {
        val p = CourseFollower(route).locate(37.5, 127.00565)
        assertEquals(0.0, p.offsetM, 1.0)
        assertEquals(p.alongM, p.remainingM, 15.0)
    }

    @Test fun perpendicularOffsetIsMeasured() {
        val p = CourseFollower(route).locate(37.5 + 60 / mPerDegLat, 127.005)
        assertEquals(60.0, p.offsetM, 1.0)
    }

    @Test fun beyondEndUsesEndpoint() {
        val f = CourseFollower(route)
        val p = f.locate(37.5, 127.0113 + 0.001)
        assertEquals(88.0, p.offsetM, 3.0)
        assertEquals(0.0, p.remainingM, 1e-6)
    }

    @Test fun offCourseTriggersOnceWithHysteresis() {
        val f = CourseFollower(route)
        assertFalse(f.update(37.5 + 20 / mPerDegLat, 127.005).second)
        assertTrue(f.update(37.5 + 55 / mPerDegLat, 127.005).second)     // 이탈 시작 → 진동
        assertFalse(f.update(37.5 + 70 / mPerDegLat, 127.005).second)    // 계속 이탈, 재진동 없음
        assertFalse(f.update(37.5 + 40 / mPerDegLat, 127.005).second); assertTrue(f.isOffCourse) // 35~50m: 아직 이탈
        f.update(37.5 + 10 / mPerDegLat, 127.005); assertFalse(f.isOffCourse)
    }

    @Test fun codecRoundTrip() {
        val pts = listOf(GpxPoint(37.123456, 127.654321, 812.3), GpxPoint(37.2, 127.3, null))
        val back = CoursePointCodec.decode(CoursePointCodec.encode(pts))
        assertEquals(pts, back)
    }

    @Test fun reversedRouteStartsAtOriginalEndAndEndsAtOriginalStart() {
        val revFollower = CourseFollower(route.reversed())
        val nearOriginalEnd = revFollower.locate(route.last().lat, route.last().lon)
        assertEquals(0.0, nearOriginalEnd.alongM, 1.0)
        assertEquals(revFollower.totalM, nearOriginalEnd.remainingM, 1.0)

        val nearOriginalStart = revFollower.locate(route.first().lat, route.first().lon)
        assertEquals(revFollower.totalM, nearOriginalStart.alongM, 1.0)
        assertEquals(0.0, nearOriginalStart.remainingM, 1.0)
    }

    @Test fun reversedRuggednessSwapsAscentAndDescent() {
        // 정방향: 0m → 220m 꾸준히 오르막 (약 2km) 후 190m 로 짧게 내리막
        val uphillPts = (0..40).map { i ->
            val ele = if (i <= 32) i * (220.0 / 32.0) else 220.0 - (i - 32) * (30.0 / 8.0)
            GpxPoint(37.50 + i * 0.00045, 127.00, ele)
        }
        val fwd = GpxRuggedness.analyze("코스", uphillPts)
        val rev = GpxRuggedness.analyze("코스", uphillPts.reversed())

        assertTrue(fwd.ascentM > 180.0 && fwd.descentM < 50.0)
        assertEquals(fwd.ascentM, rev.descentM, 15.0)
        assertEquals(fwd.descentM, rev.ascentM, 15.0)
        assertEquals(fwd.distanceM, rev.distanceM, 1e-3)
    }
}
