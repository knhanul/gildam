package kr.co.nuni.gildam.data.gpx

import kr.co.nuni.gildam.data.local.entity.TrackPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackGapDetectorTest {

    private fun pt(
        seq: Int,
        recordedAtMs: Long,
        isPauseBoundary: Boolean = false
    ) = TrackPointEntity(
        id = seq.toLong(),
        activityId = "act-gap-test",
        seq = seq,
        latitude = 37.5,
        longitude = 127.0,
        recordedAtEpochMs = recordedAtMs,
        isPauseBoundary = isPauseBoundary
    )

    @Test
    fun gapOverThreeMinutesIsCountedAsOne() {
        val base = 1_000_000L
        val points = listOf(
            pt(1, base),
            pt(2, base + 1_000L),
            pt(3, base + 1_000L + TrackGapDetector.GAP_THRESHOLD_MS + 5_000L), // 3분 초과 벌어진 구간
            pt(4, base + 1_000L + TrackGapDetector.GAP_THRESHOLD_MS + 6_000L)
        )
        assertTrue(TrackGapDetector.hasGap(points))
        assertEquals(1, TrackGapDetector.countGaps(points))
    }

    @Test
    fun pauseBoundaryIsNotCountedAsGap() {
        val base = 1_000_000L
        val points = listOf(
            pt(1, base),
            pt(2, base + 1_000L),
            pt(3, base + 600_000L, isPauseBoundary = true), // 10분 차이이지만 일시정지 경계
            pt(4, base + 601_000L)
        )
        assertFalse(TrackGapDetector.hasGap(points))
        assertEquals(0, TrackGapDetector.countGaps(points))
    }

    @Test
    fun zeroOrOnePointHasNoGap() {
        assertFalse(TrackGapDetector.hasGap(emptyList()))
        assertEquals(0, TrackGapDetector.countGaps(emptyList()))

        val single = listOf(pt(1, 1_000_000L))
        assertFalse(TrackGapDetector.hasGap(single))
        assertEquals(0, TrackGapDetector.countGaps(single))
    }
}
