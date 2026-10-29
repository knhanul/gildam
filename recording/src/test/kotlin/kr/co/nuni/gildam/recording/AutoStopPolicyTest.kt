package kr.co.nuni.gildam.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoStopPolicyTest {

    private val hour = AutoStopPolicy.HOUR_MS
    private val min = AutoStopPolicy.MINUTE_MS

    private fun baseInput(
        nowMs: Long = 0L,
        startedAtMs: Long = 0L,
        accumulatedPauseMs: Long = 0L,
        lastMovementAtMs: Long = 0L,
        isPaused: Boolean = false,
        maxDurationMs: Long? = AutoStopPolicy.DEFAULT_MAX_DURATION_MS,
        idleLimitMs: Long? = AutoStopPolicy.DEFAULT_IDLE_LIMIT_MS,
        graceMs: Long = AutoStopPolicy.DEFAULT_GRACE_MS,
        warningShownAtMs: Long? = null
    ) = AutoStopInput(
        nowMs = nowMs,
        startedAtMs = startedAtMs,
        accumulatedPauseMs = accumulatedPauseMs,
        lastMovementAtMs = lastMovementAtMs,
        isPaused = isPaused,
        maxDurationMs = maxDurationMs,
        idleLimitMs = idleLimitMs,
        graceMs = graceMs,
        warningShownAtMs = warningShownAtMs
    )

    @Test
    fun disabledOptionsAlwaysReturnNone() {
        val input = baseInput(
            nowMs = 20 * hour,
            startedAtMs = 0L,
            lastMovementAtMs = 0L,
            maxDurationMs = null,
            idleLimitMs = null
        )
        assertEquals(AutoStopDecision.None, decide(input))
    }

    @Test
    fun maxDurationExceededReturnsWarnThenStopAfterGrace() {
        val warnAt = 4 * hour
        val warnDecision = decide(
            baseInput(
                nowMs = warnAt + 1_000L,
                startedAtMs = 0L,
                lastMovementAtMs = warnAt,
                maxDurationMs = 4 * hour,
                idleLimitMs = 20 * min,
                graceMs = 10 * min,
                warningShownAtMs = null
            )
        )
        assertEquals(AutoStopDecision.Warn(StopReason.MAX_DURATION), warnDecision)

        // 경고 후 grace 이내 → Warn 유지 (중복 알림 없음)
        val withinGrace = decide(
            baseInput(
                nowMs = warnAt + 5 * min,
                startedAtMs = 0L,
                lastMovementAtMs = warnAt,
                maxDurationMs = 4 * hour,
                idleLimitMs = 20 * min,
                graceMs = 10 * min,
                warningShownAtMs = warnAt
            )
        )
        assertEquals(AutoStopDecision.Warn(StopReason.MAX_DURATION), withinGrace)

        // 경고 후 grace 초과 → Stop(MAX_DURATION)
        val afterGrace = decide(
            baseInput(
                nowMs = warnAt + 10 * min + 1_000L,
                startedAtMs = 0L,
                lastMovementAtMs = warnAt + 9 * min,
                maxDurationMs = 4 * hour,
                idleLimitMs = 20 * min,
                graceMs = 10 * min,
                warningShownAtMs = warnAt
            )
        )
        assertEquals(AutoStopDecision.Stop(StopReason.MAX_DURATION), afterGrace)
    }

    @Test
    fun idleExceededReturnsWarnThenStopAfterGrace() {
        val warnAt = 20 * min
        val warnDecision = decide(
            baseInput(
                nowMs = warnAt + 1_000L,
                startedAtMs = 0L,
                lastMovementAtMs = 0L,
                maxDurationMs = 4 * hour,
                idleLimitMs = 20 * min,
                graceMs = 10 * min,
                warningShownAtMs = null
            )
        )
        assertEquals(AutoStopDecision.Warn(StopReason.IDLE), warnDecision)

        val stopDecision = decide(
            baseInput(
                nowMs = warnAt + 10 * min + 1_000L,
                startedAtMs = 0L,
                lastMovementAtMs = 0L,
                maxDurationMs = 4 * hour,
                idleLimitMs = 20 * min,
                graceMs = 10 * min,
                warningShownAtMs = warnAt
            )
        )
        assertEquals(AutoStopDecision.Stop(StopReason.IDLE), stopDecision)
    }

    @Test
    fun pausedAlwaysReturnsNone() {
        val pausedMax = baseInput(
            nowMs = 10 * hour,
            startedAtMs = 0L,
            lastMovementAtMs = 0L,
            isPaused = true,
            maxDurationMs = 2 * hour,
            idleLimitMs = 10 * min
        )
        assertEquals(AutoStopDecision.None, decide(pausedMax))
    }

    @Test
    fun continueResetsWarningAndExtendsFromContinueTimestamp() {
        val warnAt = 4 * hour
        val warnedInput = baseInput(
            nowMs = warnAt + 2 * min,
            startedAtMs = 0L,
            lastMovementAtMs = warnAt,
            maxDurationMs = 4 * hour,
            idleLimitMs = 20 * min,
            graceMs = 10 * min,
            warningShownAtMs = warnAt
        )
        assertTrue(decide(warnedInput) is AutoStopDecision.Warn)

        val continueAt = warnAt + 3 * min
        val resetInput = AutoStopPolicy.resetAfterContinue(warnedInput, continueAtMs = continueAt)

        // 리셋 직후에는 None
        assertEquals(
            AutoStopDecision.None,
            decide(resetInput.copy(nowMs = continueAt + 1 * hour, lastMovementAtMs = continueAt + 50 * min))
        )

        // 새 기준 시점부터 다시 maxDurationMs(4시간)가 지나야 다시 Warn(MAX_DURATION)
        assertEquals(
            AutoStopDecision.Warn(StopReason.MAX_DURATION),
            decide(
                resetInput.copy(
                    nowMs = continueAt + 4 * hour + 1_000L,
                    lastMovementAtMs = continueAt + 4 * hour
                )
            )
        )
    }
}
