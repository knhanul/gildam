package kr.co.nuni.gildam.recording

import kr.co.nuni.gildam.data.gpx.GpxExporter
import kr.co.nuni.gildam.data.local.entity.TrackPointEntity
import kotlin.math.max

data class TruncatedRecordingStats(
    val keptPoints: List<TrackPointEntity>,
    val cutoffMs: Long,
    val endedAtEpochMs: Long,
    val totalDurationMs: Long,
    val movingDurationMs: Long,
    val distanceMeters: Double,
    val avgSpeedMps: Float,
    val movingAvgSpeedMps: Float,
    val maxSpeedMps: Float,
    val elevationGainMeters: Float
)

/**
 * 오래 멈춤(IDLE) 자동 종료 시 마지막 움직임 시각 + 30초 이후 정지 구간을 잘라 내고
 * 남은 점들만으로 거리·이동 시간·평균 속도를 다시 계산하는 순수 함수.
 */
object StopTruncation {

    fun truncateForIdleStop(
        points: List<TrackPointEntity>,
        startedAtMs: Long,
        accumulatedPauseMs: Long,
        lastMovementAtMs: Long,
        tailMs: Long = AutoStopPolicy.LAST_MOVEMENT_TAIL_MS
    ): TruncatedRecordingStats {
        val cutoffMs = lastMovementAtMs + tailMs
        val kept = points
            .filter { it.recordedAtEpochMs <= cutoffMs }
            .sortedBy { it.seq }

        val totalDurationMs = (cutoffMs - startedAtMs - accumulatedPauseMs).coerceAtLeast(0L)

        var distanceMeters = 0.0
        var movingDurationMs = 0L
        var maxSpeedMps = 0f
        var elevationGainMeters = 0f

        for (i in kept.indices) {
            val curr = kept[i]
            val spd = curr.speedMps ?: 0f
            if (spd > maxSpeedMps) maxSpeedMps = spd

            if (i > 0 && !curr.isPauseBoundary) {
                val prev = kept[i - 1]
                val deltaMeters = GpxExporter.haversineMeters(
                    prev.latitude, prev.longitude,
                    curr.latitude, curr.longitude
                )
                if (deltaMeters >= AutoStopPolicy.MIN_MOVEMENT_METERS) {
                    distanceMeters += deltaMeters
                    movingDurationMs += (curr.recordedAtEpochMs - prev.recordedAtEpochMs).coerceIn(0L, 5000L)
                }
                val currAlt = curr.altitudeMeters
                val prevAlt = prev.altitudeMeters
                if (currAlt != null && prevAlt != null) {
                    val altDiff = currAlt - prevAlt
                    if (altDiff in 0.5f..15.0f) {
                        elevationGainMeters += altDiff
                    }
                }
            }
        }

        val avgSpeedMps = if (totalDurationMs > 1000L) {
            (distanceMeters / (totalDurationMs / 1000.0)).toFloat()
        } else {
            0f
        }

        val movingAvgSpeedMps = if (movingDurationMs > 1000L) {
            (distanceMeters / (movingDurationMs / 1000.0)).toFloat()
        } else {
            0f
        }

        return TruncatedRecordingStats(
            keptPoints = kept,
            cutoffMs = cutoffMs,
            endedAtEpochMs = cutoffMs,
            totalDurationMs = totalDurationMs,
            movingDurationMs = max(0L, movingDurationMs),
            distanceMeters = distanceMeters,
            avgSpeedMps = avgSpeedMps,
            movingAvgSpeedMps = movingAvgSpeedMps,
            maxSpeedMps = maxSpeedMps,
            elevationGainMeters = elevationGainMeters
        )
    }
}
