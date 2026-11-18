package kr.co.nuni.gildam.recording

import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.data.weather.WeatherNow

data class LatLonPoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Float? = null,
    val isPauseBoundary: Boolean = false
)

/**
 * 기록 중 날씨 상태.
 * - [weather] 가 null 이면 아직 받지 못했거나 오프라인·미로그인 → 계기판에 '–' 표시.
 * - [fetchedAtEpochMs] 기준으로 60분이 넘은 값은 화면에서 흐리게 표시합니다.
 */
data class LiveWeatherState(
    val weather: WeatherNow? = null,
    val fetchedAtEpochMs: Long = 0L
) {
    fun isStale(nowEpochMs: Long, staleAfterMs: Long = STALE_AFTER_MS): Boolean =
        weather != null && fetchedAtEpochMs > 0L && (nowEpochMs - fetchedAtEpochMs) > staleAfterMs

    companion object {
        const val REFRESH_INTERVAL_MS = 30 * 60 * 1000L
        const val STALE_AFTER_MS = 60 * 60 * 1000L
    }
}

data class LiveRecordingState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val activeActivityId: String? = null,
    val transportType: TransportType = TransportType.WALK,
    val elapsedTotalMs: Long = 0L,
    val elapsedMovingMs: Long = 0L,
    val distanceMeters: Double = 0.0,
    val currentSpeedMps: Float = 0f,
    val movingAvgSpeedMps: Float = 0f,
    val maxSpeedMps: Float = 0f,
    val elevationGainM: Float = 0f,
    val elevationLossM: Float = 0f,
    val liveWeather: LiveWeatherState = LiveWeatherState(),
    val currentBearingDeg: Float? = null,
    val currentLocation: LatLonPoint? = null,
    /**
     * 지도에 그릴 **화면용** 경로 (줄인 점, 약 2,000점 이하). 원본 점은 DB 에만 있고,
     * 거리·저장·동기화는 원본을 씁니다.
     */
    val liveTrackPoints: List<LatLonPoint> = emptyList(),
    /** 지금까지 받은 원본 점 수 (경로가 바뀌었는지 보는 버전 번호) */
    val liveTrackPointCount: Int = 0,
    val isGpsSignalWeak: Boolean = false,
    /** 방금 끝난 기록의 마지막 위치·경로. 새 기록을 시작하면 비워집니다. */
    val lastFinishedLocation: LatLonPoint? = null,
    val lastFinishedTrackPoints: List<LatLonPoint> = emptyList()
) {
    /** 기존 호출부 호환 별칭 */
    val elevationGainMeters: Float get() = elevationGainM

    /** 기록 종료 시 계기판 값은 0으로 되돌리고 위치·경로는 `lastFinished*` 스냅샷으로 옮깁니다. */
    fun finished(): LiveRecordingState = LiveRecordingState(
        lastFinishedLocation = currentLocation ?: liveTrackPoints.lastOrNull(),
        lastFinishedTrackPoints = liveTrackPoints
    )
}
