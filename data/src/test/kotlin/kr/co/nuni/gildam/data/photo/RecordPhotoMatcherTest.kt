package kr.co.nuni.gildam.data.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordPhotoMatcherTest {
    private val start = 1_761_000_000_000L
    private val end = start + 3_600_000L // 1시간 기록
    // 약 89m 간격으로 동쪽으로 이동하는 경로 (위도 37.5)
    private val route = RecordPhotoMatcher.routeWithDistance(
        (0..36).map { i -> Triple(37.50, 127.00 + i * 0.0010, start + i * 100_000L) }
    )

    @Test fun keepsPhotoInsideWindowAndNearTrack() {
        val p = PhotoCandidate("content://p1", start + 600_000L, 37.50005, 127.0033)
        val m = RecordPhotoMatcher.match(start, end, route, listOf(p))
        assertEquals(1, m.size)
        assertFalse(m[0].timeOnly)
        assertTrue(m[0].offsetM!! < 50.0)
        assertTrue(m[0].alongTrackM!! in 250.0..400.0)
        assertEquals("0.3 km 지점", RecordPhotoMatcher.describePosition(m[0])?.substringBefore(" ·"))
    }

    @Test fun dropsPhotoTooFarFromTrack() {
        // 시간은 안이지만 경로에서 약 1km 북쪽
        val p = PhotoCandidate("content://far", start + 600_000L, 37.509, 127.003)
        assertTrue(RecordPhotoMatcher.match(start, end, route, listOf(p)).isEmpty())
    }

    @Test fun dropsPhotoOutsideTimeWindow() {
        val before = PhotoCandidate("content://early", start - 10 * 60_000L, 37.50, 127.003)
        val after = PhotoCandidate("content://late", end + 10 * 60_000L, 37.50, 127.003)
        assertTrue(RecordPhotoMatcher.match(start, end, route, listOf(before, after)).isEmpty())
    }

    @Test fun graceAllowsFiveMinutesAroundRecord() {
        val almost = PhotoCandidate("content://grace", start - 4 * 60_000L, 37.50, 127.0005)
        assertEquals(1, RecordPhotoMatcher.match(start, end, route, listOf(almost)).size)
    }

    @Test fun photoWithoutGpsIsMatchedByTimeOnly() {
        val p = PhotoCandidate("content://nogps", start + 900_000L, null, null)
        val m = RecordPhotoMatcher.match(start, end, route, listOf(p))
        assertEquals(1, m.size)
        assertTrue(m[0].timeOnly)
        assertNull(m[0].offsetM)
    }

    @Test fun resultsAreSortedByTime() {
        val a = PhotoCandidate("content://b", start + 900_000L, 37.50, 127.002)
        val b = PhotoCandidate("content://a", start + 100_000L, 37.50, 127.001)
        val m = RecordPhotoMatcher.match(start, end, route, listOf(a, b))
        assertEquals(listOf("content://a", "content://b"), m.map { it.uri })
    }

    @Test fun queryWindowHasGraceOnBothSides() {
        val (from, to) = RecordPhotoMatcher.queryWindow(start, end)
        assertEquals(start - RecordPhotoMatcher.GRACE_MS, from)
        assertEquals(end + RecordPhotoMatcher.GRACE_MS, to)
    }

    @Test fun matchedPhotosCarryMapPinCoordinates() {
        val gps = PhotoCandidate("content://p1", start + 600_000L, 37.50005, 127.0033)
        val noGps = PhotoCandidate("content://nogps", start + 900_000L, null, null)
        val m = RecordPhotoMatcher.match(start, end, route, listOf(gps, noGps))
        // GPS 사진: 사진이 찍힌 실제 좌표
        assertEquals(37.50005, m[0].pointLat!!, 1e-9)
        assertEquals(127.0033, m[0].pointLon!!, 1e-9)
        // GPS 없는 사진: 가장 가까운 시각의 경로 점 (지도 핀을 놓을 수 있게)
        assertTrue(m[1].timeOnly)
        assertEquals(37.50, m[1].pointLat!!, 1e-9)
        assertTrue(m[1].pointLon!! > 127.00)
    }
}
