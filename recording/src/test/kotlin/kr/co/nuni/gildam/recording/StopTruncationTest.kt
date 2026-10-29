package kr.co.nuni.gildam.recording

import kr.co.nuni.gildam.data.gpx.GpxExporter
import kr.co.nuni.gildam.data.local.entity.TrackPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StopTruncationTest {

    private fun pt(
        seq: Int,
        lat: Double,
        lon: Double,
        timeMs: Long,
        speedMps: Float = 1.4f,
        alt: Float? = 50f,
        isPauseBoundary: Boolean = false
    ) = TrackPointEntity(
        id = seq.toLong(),
        activityId = "act-idle-1",
        seq = seq,
        latitude = lat,
        longitude = lon,
        altitudeMeters = alt,
        speedMps = speedMps,
        recordedAtEpochMs = timeMs,
        isPauseBoundary = isPauseBoundary
    )

    @Test
    fun pointsAfterCutoffAreExcludedAndEndedAtEqualsCutoff() {
        val startedAt = 1_000_000L
        val lastMoveAt = startedAt + 60_000L // 1분 동안 이동 후 정지
        val expectedCutoff = lastMoveAt + AutoStopPolicy.LAST_MOVEMENT_TAIL_MS // +30초

        val p1 = pt(1, 37.5000, 127.0000, startedAt)
        val p2 = pt(2, 37.5005, 127.0000, startedAt + 30_000L)
        val p3 = pt(3, 37.5010, 127.0000, lastMoveAt)
        // 마지막 움직임 +20초 (tail 30초 안쪽 → 유지)
        val p4InTail = pt(4, 37.5010, 127.0000, lastMoveAt + 20_000L, speedMps = 0f)
        // 마지막 움직임 +31초, +10분 (cutoff 이후 → 제외되어야 함)
        val p5AfterCutoff = pt(5, 37.5010, 127.0000, expectedCutoff + 1_000L, speedMps = 0f)
        val p6AfterCutoff = pt(6, 37.5010, 127.0000, lastMoveAt + 600_000L, speedMps = 0f)

        val stats = StopTruncation.truncateForIdleStop(
            points = listOf(p1, p2, p3, p4InTail, p5AfterCutoff, p6AfterCutoff),
            startedAtMs = startedAt,
            accumulatedPauseMs = 0L,
            lastMovementAtMs = lastMoveAt
        )

        assertEquals(expectedCutoff, stats.cutoffMs)
        assertEquals(expectedCutoff, stats.endedAtEpochMs)
        assertEquals(90_000L, stats.totalDurationMs)
        assertEquals(listOf(1, 2, 3, 4), stats.keptPoints.map { it.seq })
        assertTrue(stats.keptPoints.all { it.recordedAtEpochMs <= expectedCutoff })
    }

    @Test
    fun distanceAndSpeedsAreRecalculatedFromRemainingPoints() {
        val startedAt = 1_000_000L
        val p1 = pt(1, 37.5000, 127.0000, startedAt, speedMps = 1.5f)
        val p2 = pt(2, 37.5001, 127.0000, startedAt + 4_000L, speedMps = 2.5f)
        val p3 = pt(3, 37.5002, 127.0000, startedAt + 8_000L, speedMps = 2.0f)
        // cutoff 이후 멀리 떨어진 노이즈/드리프트 점 (잘려 나가서 거리에 반영되면 안 됨)
        val p4After = pt(4, 37.5100, 127.0000, startedAt + 8_000L + 40_000L, speedMps = 9.0f)

        val lastMoveAt = startedAt + 8_000L
        val stats = StopTruncation.truncateForIdleStop(
            points = listOf(p1, p2, p3, p4After),
            startedAtMs = startedAt,
            accumulatedPauseMs = 0L,
            lastMovementAtMs = lastMoveAt
        )

        val expectedDist =
            GpxExporter.haversineMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude) +
                GpxExporter.haversineMeters(p2.latitude, p2.longitude, p3.latitude, p3.longitude)

        assertEquals(expectedDist, stats.distanceMeters, 1e-6)
        assertEquals(8_000L, stats.movingDurationMs)
        assertEquals((expectedDist / 8.0).toFloat(), stats.movingAvgSpeedMps, 1e-4f)
        assertEquals((expectedDist / 38.0).toFloat(), stats.avgSpeedMps, 1e-4f)
        assertEquals(2.5f, stats.maxSpeedMps, 1e-6f)
    }
}
