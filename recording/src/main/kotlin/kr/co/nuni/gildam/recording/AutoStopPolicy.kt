package kr.co.nuni.gildam.recording

enum class StopReason { MANUAL, MAX_DURATION, IDLE }

data class AutoStopInput(
    val nowMs: Long,
    val startedAtMs: Long,
    val accumulatedPauseMs: Long,
    val lastMovementAtMs: Long,
    val isPaused: Boolean,
    val maxDurationMs: Long?,      // null = 사용 안 함
    val idleLimitMs: Long?,        // null = 사용 안 함
    val graceMs: Long,             // 확인 대기
    val warningShownAtMs: Long?    // null = 아직 알림 없음
)

sealed interface AutoStopDecision {
    data object None : AutoStopDecision
    data class Warn(val reason: StopReason) : AutoStopDecision   // 알림만
    data class Stop(val reason: StopReason) : AutoStopDecision   // 실제 종료
}

fun decide(input: AutoStopInput): AutoStopDecision = AutoStopPolicy.decide(input)

object AutoStopPolicy {
    const val MINUTE_MS = 60_000L
    const val HOUR_MS = 3_600_000L

    const val IDLE_SPEED_MPS = 0.5f
    const val MIN_MOVEMENT_METERS = 0.8
    const val LAST_MOVEMENT_TAIL_MS = 30_000L

    const val DEFAULT_MAX_DURATION_MS: Long = 4 * HOUR_MS
    const val DEFAULT_IDLE_LIMIT_MS: Long = 20 * MINUTE_MS
    const val DEFAULT_GRACE_MS: Long = 10 * MINUTE_MS

    val MAX_DURATION_OPTIONS_MS: List<Long?> = listOf(
        2 * HOUR_MS,
        4 * HOUR_MS,
        6 * HOUR_MS,
        8 * HOUR_MS,
        10 * HOUR_MS,
        null
    )

    val IDLE_LIMIT_OPTIONS_MS: List<Long?> = listOf(
        10 * MINUTE_MS,
        20 * MINUTE_MS,
        30 * MINUTE_MS,
        40 * MINUTE_MS,
        50 * MINUTE_MS,
        60 * MINUTE_MS,
        null
    )

    val GRACE_OPTIONS_MS: List<Long> = listOf(
        5 * MINUTE_MS,
        10 * MINUTE_MS,
        15 * MINUTE_MS
    )

    fun decide(input: AutoStopInput): AutoStopDecision {
        if (input.isPaused) return AutoStopDecision.None

        val activeElapsedMs = (input.nowMs - input.startedAtMs - input.accumulatedPauseMs).coerceAtLeast(0L)
        val idleElapsedMs = (input.nowMs - input.lastMovementAtMs).coerceAtLeast(0L)

        val reason = when {
            input.maxDurationMs != null && activeElapsedMs >= input.maxDurationMs -> StopReason.MAX_DURATION
            input.idleLimitMs != null && idleElapsedMs >= input.idleLimitMs -> StopReason.IDLE
            else -> null
        } ?: return AutoStopDecision.None

        val warnedAt = input.warningShownAtMs ?: return AutoStopDecision.Warn(reason)
        return if (input.nowMs - warnedAt >= input.graceMs) {
            AutoStopDecision.Stop(reason)
        } else {
            AutoStopDecision.Warn(reason)
        }
    }

    /**
     * 경고 알림에서 사용자가 '계속 기록'을 눌렀을 때:
     * warningShownAtMs를 null로 리셋하고, 그 시점을 새 기준으로 다시 셉니다.
     */
    fun resetAfterContinue(input: AutoStopInput, continueAtMs: Long = input.nowMs): AutoStopInput =
        input.copy(
            startedAtMs = continueAtMs,
            accumulatedPauseMs = 0L,
            lastMovementAtMs = continueAtMs,
            warningShownAtMs = null
        )

    fun formatMaxDurationLabel(durationMs: Long?): String =
        if (durationMs == null) "사용 안 함" else "${durationMs / HOUR_MS}시간"

    fun formatIdleLimitLabel(limitMs: Long?): String =
        if (limitMs == null) "사용 안 함" else "${limitMs / MINUTE_MS}분"

    fun formatGraceLabel(graceMs: Long): String = "${graceMs / MINUTE_MS}분"
}
