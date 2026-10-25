package kr.co.nuni.gildam.map

import kr.co.nuni.gildam.recording.LatLonPoint
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
        assertNull(MapCameraPlanner.overviewFit(emptyList(), null, emptyList(), pad))
    }

    @Test fun paddingReservesCardsButNeverMostOfScreen() {
        val a = MapCameraPlanner.overlayPadding(1080, 2400, topCardsPx = 300, bottomCardPx = 900)
        assertEquals(348, a.top); assertEquals(948, a.bottom); assertEquals(48, a.left)
        val b = MapCameraPlanner.overlayPadding(1080, 1000, topCardsPx = 500, bottomCardPx = 900)
        assertTrue(b.top + b.bottom <= 800)
        assertTrue(b.bottom > b.top)
    }

    @Test fun refitRules() {
        val last = GeoBounds(37.0, 127.0, 37.1, 127.1)
        val same = GeoBounds(37.02, 127.02, 37.08, 127.08)
        assertTrue(MapCameraPlanner.needsRefit(null, same, null, 0, 0))
        // 안쪽에 있고 시간 안 지남 → 안 함
        assertFalse(MapCameraPlanner.needsRefit(last, same, p(37.05, 127.05), 1_000, 0))
        // 가장자리 10% 밖 → 바로
        assertTrue(MapCameraPlanner.needsRefit(last, same, p(37.095, 127.05), 1_000, 0))
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
}
