package kr.co.nuni.gildam.data.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoLocationTest {
    private val start = 1_761_000_000_000L
    private val end = start + 3_600_000L
    // 100초마다 동쪽으로 0.001도(약 88m)
    private val route = RecordPhotoMatcher.routeWithDistance(
        (0..36).map { i -> Triple(37.50, 127.00 + i * 0.0010, start + i * 100_000L) }
    )

    @Test fun interpolatesBetweenTrackPoints() {
        val p = RecordPhotoMatcher.positionAt(route, start + 150_000L)!!
        assertEquals(127.0015, p.longitude, 1e-9)
        assertEquals((route[1].alongM + route[2].alongM) / 2, p.alongM, 1e-6)
    }

    @Test fun clampsBeforeStartAndAfterEnd() {
        assertEquals(route.first(), RecordPhotoMatcher.positionAt(route, start - 60_000L))
        assertEquals(route.last(), RecordPhotoMatcher.positionAt(route, end + 600_000L))
        assertNull(RecordPhotoMatcher.positionAt(emptyList(), start))
    }

    @Test fun longGapUsesNearerPointInsteadOfInterpolating() {
        val gapRoute = RecordPhotoMatcher.routeWithDistance(listOf(
            Triple(37.50, 127.000, start),
            Triple(37.50, 127.010, start + 20 * 60_000L) // 20분 멈춤 뒤
        ))
        val p = RecordPhotoMatcher.positionAt(gapRoute, start + 2 * 60_000L)!!
        assertEquals(127.000, p.longitude, 1e-9)
    }

    @Test fun noGpsPhotoGetsRouteEstimatedPosition() {
        val m = RecordPhotoMatcher.match(start, end, route, listOf(PhotoCandidate("content://x", start + 150_000L)))
        assertTrue(m[0].timeOnly)
        assertTrue(m[0].routeEstimated)
        assertFalse(m[0].noLocation)
        assertEquals(127.0015, m[0].pointLon!!, 1e-9)
    }

    @Test fun noGpsAndNoRouteIsNoLocation() {
        val m = RecordPhotoMatcher.match(start, end, emptyList(), listOf(PhotoCandidate("content://x", start + 150_000L)))
        assertTrue(m[0].noLocation)
        assertFalse(m[0].routeEstimated)
    }

    @Test fun redactedOrBogusGpsIsTreatedAsMissing() {
        assertNull(DevicePhotoFinder.usableGps(0.0, 0.0))
        assertNull(DevicePhotoFinder.usableGps(Double.NaN, 127.0))
        assertNull(DevicePhotoFinder.usableGps(91.0, 127.0))
        assertEquals(37.5 to 127.0, DevicePhotoFinder.usableGps(37.5, 127.0))
    }

    private fun photo(uri: String, at: Long, lat: Double?, lon: Double?, timeOnly: Boolean = false) =
        MatchedPhoto(uri, at, null, null, timeOnly, lat, lon)

    @Test fun waypointFilterPicksNearbyByPlaceOrTimeAndLinked() {
        val anchor = WaypointPhotoFilter.Anchor(37.50, 127.010, start + 1_000_000L)
        val photos = listOf(
            photo("content://media/external/images/media/1", start + 100_000L, 37.5002, 127.0101), // 약 22m: 포함
            photo("content://media/external/images/media/2", start + 1_100_000L, 37.51, 127.03),   // 멀지만 100초 뒤: 포함
            photo("content://media/external/images/media/3", start + 3_000_000L, 37.50, 127.02),   // 멀고 시간도 멂: 제외
            photo("content://media/external/images/media/4", start + 2_000_000L, null, null, true) // 위치 모름·시간 멂: 제외
        )
        val linked = listOf(
            photo("content://media/external_primary/images/media/2", start + 1_100_000L, 37.51, 127.03), // 목록과 같은 사진
            photo("content://media/external/images/media/9", start + 900_000L, 37.50, 127.010)          // 목록에 없던 사진
        )
        val out = WaypointPhotoFilter.select(anchor, photos, linked)
        assertEquals(
            listOf("content://media/external/images/media/1", "content://media/external/images/media/9",
                "content://media/external/images/media/2"),
            out.map { it.uri }
        )
    }

    @Test fun mediaKeyIgnoresVolumeName() {
        assertEquals(
            WaypointPhotoFilter.mediaKey("content://media/external/images/media/42"),
            WaypointPhotoFilter.mediaKey("content://media/external_primary/images/media/42")
        )
        assertEquals("file:///x.jpg", WaypointPhotoFilter.mediaKey("file:///x.jpg"))
    }
}
