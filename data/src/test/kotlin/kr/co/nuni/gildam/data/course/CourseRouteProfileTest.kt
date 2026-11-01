package kr.co.nuni.gildam.data.course

import kr.co.nuni.gildam.data.gpxcompare.GpxPoint
import kr.co.nuni.gildam.data.gpxcompare.ProfilePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseRouteProfileTest {
    // 경도 0.01° ≈ 887m (위도 37.5 기준)
    private val route = listOf(
        GpxPoint(37.50, 127.00, 100.0),
        GpxPoint(37.50, 127.01, 150.0),
        GpxPoint(37.50, 127.02, 120.0)
    )
    private val profile = CourseRouteProfile(route)

    @Test fun totalDistanceMatchesSegmentSum() {
        assertEquals(1.774, profile.totalM / 1000, 0.01)
    }

    @Test fun distanceMapsToInterpolatedPoint() {
        val mid = profile.pointAt(profile.totalM / 2000.0)!! // 전체의 절반 = 1번째 점 부근
        assertEquals(37.50, mid.lat, 1e-9)
        assertEquals(127.01, mid.lon, 2e-3)
        // 고도도 선형 보간 (100 → 150 사이)
        assertTrue(mid.ele!! in 100.0..150.0)
    }

    @Test fun outOfRangeClampsToEnds() {
        assertEquals(127.00, profile.pointAt(-5.0)!!.lon, 1e-9)
        assertEquals(127.02, profile.pointAt(999.0)!!.lon, 1e-9)
    }

    @Test fun nearestDistanceSnapsToCourseWithinTolerance() {
        // 코스 선에서 약 30m 남쪽 → 거리 0 근처
        val d = profile.nearestDistanceKm(37.49973, 127.005, maxDistanceM = 120.0)!!
        assertEquals(0.44, d, 0.06)
        // 2km 떨어지면 null (허용 거리 밖)
        assertNull(profile.nearestDistanceKm(37.60, 127.05, maxDistanceM = 120.0))
    }

    @Test fun nearestDistanceUsesProjectionBetweenVertices() {
        // 첫 선분 중간에서 20m 위 → 약 0.44km 지점
        val d = profile.nearestDistanceKm(37.50018, 127.005)!!
        assertEquals(0.44, d, 0.05)
    }

    @Test fun graphTapMapsToDistance() {
        val prof = listOf(ProfilePoint(0.0, 100.0), ProfilePoint(1.0, 200.0), ProfilePoint(2.0, 150.0))
        // 왼쪽 여백 40px, 폭 300px, 전체 2km → 중앙 탭 = 1km
        assertEquals(1.0, ElevationChartHit.distanceKmForTap(40f + 150f, 40f, 300f, 2.0), 1e-6)
        // 왼쪽 밖 / 오른쪽 밖은 양 끝으로
        assertEquals(0.0, ElevationChartHit.distanceKmForTap(0f, 40f, 300f, 2.0), 1e-6)
        assertEquals(2.0, ElevationChartHit.distanceKmForTap(999f, 40f, 300f, 2.0), 1e-6)
        // x=1km 의 고도는 200m
        assertEquals(200.0, ElevationChartHit.elevationAt(prof, 1.0)!!, 1e-9)
        assertEquals(175.0, ElevationChartHit.elevationAt(prof, 1.5)!!, 1e-9)
        assertEquals(1, ElevationChartHit.nearestIndex(prof, 1.1))
    }
}
