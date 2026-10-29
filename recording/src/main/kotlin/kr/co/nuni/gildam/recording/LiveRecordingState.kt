package kr.co.nuni.gildam.recording

import kr.co.nuni.gildam.data.model.TransportType

data class LatLonPoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Float? = null,
    val isPauseBoundary: Boolean = false
)

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
    val elevationGainMeters: Float = 0f,
    val currentLocation: LatLonPoint? = null,
    val liveTrackPoints: List<LatLonPoint> = emptyList(),
    val isGpsSignalWeak: Boolean = false
)
