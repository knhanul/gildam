package kr.co.nuni.gildam.data.course

/**
 * 따라 걷기 '남은 시간' 계산.
 *
 * 기록 중이면 **지금 기록의 실제 이동 평균 속도**로 남은 거리를 나눕니다 (험한 길일수록 느려지므로
 * Tobler 모델보다 실제 도착 시간에 가깝습니다). 평균 속도를 믿을 수 없을 때만 기존 모델 값을 씁니다.
 */
object FollowEta {
    /** 이보다 느린 평균(≈1 km/h 미만)은 아직 걷기 전 GPS 잡음으로 보고 쓰지 않음 */
    const val MIN_SPEED_MPS = 0.3
    /** 이보다 빠른 평균(≈108 km/h)은 GPS 튐으로 보고 쓰지 않음 */
    const val MAX_SPEED_MPS = 30.0
    /** 계산 결과가 이보다 길면(하루 이상) 믿지 않고 모델 값으로 돌아감 */
    const val MAX_ETA_SEC = 24 * 3600.0

    /**
     * 기록의 이동 평균 속도(m/s). 쓸 수 없으면 null.
     * - [movingAvgSpeedMps] 가 0.3 m/s 이상이면 그대로 사용
     * - 아니면 누적 거리 ÷ 이동 시간으로 다시 계산 (이동 시간이 없으면 null)
     * - 너무 느리거나(0.3 미만) 비정상적으로 빠르면(30 초과), 또는 값이 이상하면 null
     */
    fun usableSpeedMps(movingAvgSpeedMps: Float, distanceMeters: Double, elapsedMovingMs: Long): Double? {
        val fromState = movingAvgSpeedMps.toDouble()
        val derived = if (elapsedMovingMs > 0) distanceMeters / (elapsedMovingMs / 1000.0) else Double.NaN
        val speed = if (fromState >= MIN_SPEED_MPS) fromState else derived
        if (!speed.isFinite() || speed < MIN_SPEED_MPS || speed > MAX_SPEED_MPS) return null
        return speed
    }

    /**
     * 남은 시간(초).
     * - 남은 거리가 0 이하면 0
     * - 평균 속도를 쓸 수 있으면 남은 거리 ÷ 속도
     * - 결과가 하루(24시간)를 넘거나 이상하면 [fallbackSec] (모델 값) 사용
     * - 최종값은 0~24시간으로 잘라 카드에 터무니없는 시간이 뜨지 않게 함
     */
    fun etaSeconds(remainingM: Double, speedMps: Double?, fallbackSec: Double): Double {
        val remaining = remainingM.coerceAtLeast(0.0)
        val fallback = fallbackSec.takeIf { it.isFinite() && it >= 0 } ?: 0.0
        if (remaining <= 0.0) return 0.0
        if (speedMps == null || !speedMps.isFinite() || speedMps < MIN_SPEED_MPS) return fallback.coerceIn(0.0, MAX_ETA_SEC)
        val eta = remaining / speedMps
        if (!eta.isFinite() || eta <= 0.0) return fallback.coerceIn(0.0, MAX_ETA_SEC)
        if (eta > MAX_ETA_SEC) return fallback.coerceIn(0.0, MAX_ETA_SEC)
        return eta
    }

    /** 사용한 계산이 실제 기록 속도였는지 (테스트·디버그용) */
    fun usesRecordingSpeed(speedMps: Double?, remainingM: Double): Boolean {
        if (speedMps == null || !speedMps.isFinite() || speedMps < MIN_SPEED_MPS) return false
        val remaining = remainingM.coerceAtLeast(0.0)
        if (remaining <= 0.0) return false
        val eta = remaining / speedMps
        return eta.isFinite() && eta in 0.0..MAX_ETA_SEC
    }
}
