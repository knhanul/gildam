package kr.co.nuni.gildam.map

import kr.co.nuni.gildam.recording.LatLonPoint
import kr.co.nuni.gildam.recording.LiveRecordingState
import kr.co.nuni.gildam.recording.RecordingController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapCameraPlannerTest {
    private fun p(lat: Double, lon: Double) = LatLonPoint(lat, lon)

    @Test fun boundsCoverAllPoints() {
        assertNull(MapCameraPlanner.boundsOf(emptyList()))
        val b = MapCameraPlanner.boundsOf(listOf(p(37.5, 127.0), p(37.52, 126.98), p(37.51, 127.03)))!!
        assertEquals(GeoBounds(37.5, 126.98, 37.52, 127.03), b)
    }

    @Test fun expandAddsMarginAndMinimumSpan() {
        // 기본(MARGIN_FRACTION=0.0): 충분히 긴 경로는 인위적인 상하좌우 여백 없이 원래 경계 그대로 사용
        val exact = MapCameraPlanner.expand(GeoBounds(37.0, 127.0, 37.1, 127.2))
        assertEquals(0.1, exact.latSpan, 1e-9); assertEquals(0.2, exact.lonSpan, 1e-9)
        // 명시적 fraction 지정 시 비율만큼 확장
        val e = MapCameraPlanner.expand(GeoBounds(37.0, 127.0, 37.1, 127.2), 0.1)
        assertEquals(0.12, e.latSpan, 1e-9); assertEquals(0.24, e.lonSpan, 1e-9)
        assertEquals(37.05, (e.minLat + e.maxLat) / 2, 1e-9)
        val tiny = MapCameraPlanner.expand(GeoBounds(37.0, 127.0, 37.0, 127.0))
        assertEquals(MapCameraPlanner.MIN_SPAN_DEG, tiny.latSpan, 1e-12)
    }

    @Test fun overviewIncludesCurrentAndCourse() {
        val pad = OverlayPadding(0, 0, 0, 0)
        val fit = MapCameraPlanner.overviewFit(listOf(p(37.5, 127.0)), p(37.51, 127.01), listOf(p(37.6, 127.1)), pad)!!
        assertTrue(fit.bounds.contains(p(37.6, 127.1)))
        assertTrue(fit.bounds.contains(p(37.5, 127.0)))
        assertEquals(GeoBounds(37.5, 127.0, 37.6, 127.1), fit.bounds)
        assertNull(MapCameraPlanner.overviewFit(emptyList(), null, emptyList(), pad))
    }

    @Test fun paddingReservesCardsButNeverMostOfScreen() {
        val a = MapCameraPlanner.overlayPadding(1080, 2400, topCardsPx = 300, bottomCardPx = 900)
        assertEquals(300, a.top); assertEquals(900, a.bottom); assertEquals(0, a.left); assertEquals(0, a.right)
        val b = MapCameraPlanner.overlayPadding(1080, 1000, topCardsPx = 500, bottomCardPx = 900)
        assertTrue(b.top + b.bottom <= 800)
        assertTrue(b.bottom > b.top)
    }

    @Test fun refitRules() {
        val last = GeoBounds(37.0, 127.0, 37.1, 127.1)
        val same = GeoBounds(37.02, 127.02, 37.08, 127.08)
        assertTrue(MapCameraPlanner.needsRefit(null, same, null, 0, 0))
        // 안쪽(및 경계선 위)에 있고 시간 안 지남 → 안 함
        assertFalse(MapCameraPlanner.needsRefit(last, same, p(37.05, 127.05), 1_000, 0))
        assertFalse(MapCameraPlanner.needsRefit(last, last, p(37.1, 127.05), 1_000, 0))
        // 경계 밖으로 나가면 → 바로 재맞춤
        assertTrue(MapCameraPlanner.needsRefit(last, same, p(37.105, 127.05), 1_000, 0))
        // 경로가 경계를 넘었지만 간격 전 → 대기, 간격 후 → 맞춤
        val grown = GeoBounds(37.0, 127.0, 37.15, 127.1)
        assertFalse(MapCameraPlanner.needsRefit(last, grown, p(37.05, 127.05), 3_000, 0))
        assertTrue(MapCameraPlanner.needsRefit(last, grown, p(37.05, 127.05), 5_000, 0))
        // 훨씬 작아짐 → 맞춤
        assertTrue(MapCameraPlanner.needsRefit(last, GeoBounds(37.04, 127.04, 37.05, 127.05), p(37.045, 127.045), 5_000, 0))
    }

    @Test fun followZoomWidensWithSpeedAndDistance() {
        assertEquals(17.0, MapCameraPlanner.followZoom(0f, 0.0), 1e-9)
        assertTrue(MapCameraPlanner.followZoom(6f, 0.0) < MapCameraPlanner.followZoom(1.4f, 0.0))
        assertTrue(MapCameraPlanner.followZoom(1.4f, 8_000.0) < MapCameraPlanner.followZoom(1.4f, 0.0))
        assertEquals(13.5, MapCameraPlanner.followZoom(40f, 50_000.0), 1e-9)
    }

    @Test fun targetForReturnsNullWhenNothingToShow() {
        assertNull(MapCameraPlanner.targetFor(emptyList(), null, emptyList()))
    }

    @Test fun targetForUsesLastTrackPointWithoutSeoulFallback() {
        val busanTrack = listOf(
            p(35.15, 129.05),
            p(35.16, 129.06),
            p(35.17, 129.07)
        )
        val target = MapCameraPlanner.targetFor(busanTrack, null, emptyList())!!
        assertEquals(35.17, target.latitude, 1e-9)
        assertEquals(129.07, target.longitude, 1e-9)
        assertEquals(15.2, target.zoom, 1e-9)
    }

    @Test fun targetForPrioritizesCurrentLocationOverTrackAndCourse() {
        val track = listOf(p(35.15, 129.05), p(35.17, 129.07))
        val current = p(33.4996, 126.5312) // 제주
        val course = listOf(p(36.35, 127.38))
        val target = MapCameraPlanner.targetFor(track, current, course)!!
        assertEquals(33.4996, target.latitude, 1e-9)
        assertEquals(126.5312, target.longitude, 1e-9)
    }

    @Test fun targetForFallsBackToFirstCoursePoint() {
        val course = listOf(p(35.87, 128.60), p(35.88, 128.62))
        val target = MapCameraPlanner.targetFor(emptyList(), null, course)!!
        assertEquals(35.87, target.latitude, 1e-9)
        assertEquals(128.60, target.longitude, 1e-9)
    }

    @Test fun detailOverviewFitCoversAllTrackPoints() {
        val pad = OverlayPadding(0, 0, 0, 0)
        val start = p(35.1587, 129.1604) // 해운대
        val end = p(35.1531, 129.1186)   // 광안리
        val fit = MapCameraPlanner.overviewFit(listOf(start, end), null, emptyList(), pad)!!
        assertTrue(fit.bounds.contains(start))
        assertTrue(fit.bounds.contains(end))
    }

    @Test fun finishRecordingPreservesLastTrackAndResetsGauges() {
        val track = listOf(p(35.15, 129.05), p(35.16, 129.06), p(35.17, 129.07))
        val lastLoc = p(35.17, 129.07)
        val activeState = LiveRecordingState(
            isRecording = true,
            isPaused = false,
            activeActivityId = "act-1",
            elapsedTotalMs = 120_000L,
            elapsedMovingMs = 110_000L,
            distanceMeters = 450.0,
            currentSpeedMps = 3.5f,
            movingAvgSpeedMps = 3.2f,
            maxSpeedMps = 4.1f,
            elevationGainMeters = 12f,
            currentLocation = lastLoc,
            liveTrackPoints = track
        )

        val finished = RecordingController.finishRecordingState(activeState)

        assertFalse(finished.isRecording)
        assertFalse(finished.isPaused)
        assertNull(finished.activeActivityId)
        assertNull(finished.currentLocation)
        assertTrue(finished.liveTrackPoints.isEmpty())
        assertEquals(0L, finished.elapsedTotalMs)
        assertEquals(0L, finished.elapsedMovingMs)
        assertEquals(0.0, finished.distanceMeters, 1e-9)
        assertEquals(0f, finished.currentSpeedMps, 1e-6f)
        assertEquals(0f, finished.movingAvgSpeedMps, 1e-6f)
        assertEquals(0f, finished.maxSpeedMps, 1e-6f)
        assertEquals(0f, finished.elevationGainMeters, 1e-6f)
        assertEquals(track, finished.lastFinishedTrackPoints)
        assertEquals(lastLoc, finished.lastFinishedLocation)

        // 종료 직후 화면에 넘기는 값으로 카메라 목표를 계산해도 서울이 아닌 마지막 위치가 유지됨
        val targetAfterStop = MapCameraPlanner.targetFor(
            finished.lastFinishedTrackPoints,
            finished.lastFinishedLocation,
            emptyList()
        )!!
        assertEquals(35.17, targetAfterStop.latitude, 1e-9)
        assertEquals(129.07, targetAfterStop.longitude, 1e-9)
    }

    @Test fun bearingAndResolveBearingComputeHeadingCorrectly() {
        // 정북(0°), 정동(≈90°), 정남(180°), 정서(≈270°)
        assertEquals(0.0, MapCameraPlanner.bearingDegrees(p(37.50, 127.00), p(37.51, 127.00)), 0.5)
        assertEquals(90.0, MapCameraPlanner.bearingDegrees(p(37.50, 127.00), p(37.50, 127.01)), 0.5)
        assertEquals(180.0, MapCameraPlanner.bearingDegrees(p(37.51, 127.00), p(37.50, 127.00)), 0.5)
        assertEquals(270.0, MapCameraPlanner.bearingDegrees(p(37.50, 127.01), p(37.50, 127.00)), 0.5)

        // 센서/GPS bearing이 있으면 정규화하여 우선 사용
        assertEquals(45.0, MapCameraPlanner.resolveBearing(45f, emptyList(), null), 1e-6)
        assertEquals(350.0, MapCameraPlanner.resolveBearing(-10f, emptyList(), null), 1e-6)

        // bearing이 없으면 최근 두 점의 이동 방향으로 계산
        val eastTrack = listOf(p(37.50, 127.00), p(37.50, 127.01))
        assertEquals(90.0, MapCameraPlanner.resolveBearing(null, eastTrack, null), 0.5)

        // 점이 부족하면 0.0(북쪽 위)
        assertEquals(0.0, MapCameraPlanner.resolveBearing(null, listOf(p(37.50, 127.00)), null), 1e-6)
        assertTrue(MapCameraMode.entries.contains(MapCameraMode.HEADING))
    }
}
