package kr.co.nuni.gildam.data.gpx

import kr.co.nuni.gildam.data.local.entity.TrackPointEntity

/**
 * 스키마 변경 없이 트랙 포인트 간 시각 차이로 비정상 끊김 구간을 감지하는 순수 함수.
 * - 연속된 두 점의 recordedAtEpochMs 차이가 [GAP_THRESHOLD_MS](3분) 이상이면 끊긴 구간으로 셉니다.
 * - 사용자가 의도적으로 일시정지/재개한 경계([TrackPointEntity.isPauseBoundary] == true)는 끊김으로 세지 않습니다.
 */
object TrackGapDetector {
    const val GAP_THRESHOLD_MS = 180_000L

    fun countGaps(
        points: List<TrackPointEntity>,
        thresholdMs: Long = GAP_THRESHOLD_MS
    ): Int {
        if (points.size < 2) return 0
        var count = 0
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            if (curr.isPauseBoundary) continue
            if (curr.recordedAtEpochMs - prev.recordedAtEpochMs >= thresholdMs) {
                count++
            }
        }
        return count
    }

    fun hasGap(
        points: List<TrackPointEntity>,
        thresholdMs: Long = GAP_THRESHOLD_MS
    ): Boolean = countGaps(points, thresholdMs) > 0
}
