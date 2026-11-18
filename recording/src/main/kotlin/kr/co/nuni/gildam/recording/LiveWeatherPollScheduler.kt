package kr.co.nuni.gildam.recording

/**
 * 기록 중 날씨 API(/gildam-api/v1/weather/now) 호출 주기·재시도 스케줄러 (순수 Kotlin → 단위 테스트 가능).
 * - 기록 시작 직후 위치가 잡히면 즉시 1회 호출, 성공 후에는 30분 뒤 다음 호출.
 * - 실패 시 지수 백오프(1분 → 2분 → 5분 → 최대 30분)로 조용히 재시도합니다.
 */
object LiveWeatherPollScheduler {
    const val PERIOD_MS = LiveWeatherState.REFRESH_INTERVAL_MS
    private val RETRY_DELAYS_MS = longArrayOf(
        60_000L,
        2 * 60_000L,
        5 * 60_000L,
        15 * 60_000L,
        PERIOD_MS
    )

    fun nextDelayAfterSuccessMs(): Long = PERIOD_MS

    fun nextDelayAfterFailureMs(consecutiveFailures: Int): Long {
        val idx = (consecutiveFailures - 1).coerceIn(0, RETRY_DELAYS_MS.lastIndex)
        return RETRY_DELAYS_MS[idx]
    }
}
