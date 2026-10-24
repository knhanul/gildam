package kr.co.nuni.gildam.sync.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrackUpsertRequestDto(
    @SerialName("track_id") val trackId: String,
    @SerialName("title") val title: String,
    @SerialName("transport_type") val transportType: String,
    @SerialName("started_at") val startedAtEpochMs: Long,
    @SerialName("ended_at") val endedAtEpochMs: Long?,
    @SerialName("total_duration_ms") val totalDurationMs: Long,
    @SerialName("moving_duration_ms") val movingDurationMs: Long,
    @SerialName("distance_m") val distanceMeters: Double,
    @SerialName("avg_speed_mps") val avgSpeedMps: Float,
    @SerialName("moving_avg_speed_mps") val movingAvgSpeedMps: Float,
    @SerialName("max_speed_mps") val maxSpeedMps: Float,
    @SerialName("elevation_gain_m") val elevationGainMeters: Float,
    @SerialName("visibility") val visibility: String,
    @SerialName("version") val version: Int,
    @SerialName("client_updated_at") val clientUpdatedAtEpochMs: Long
)

@Serializable
data class TrackPointDto(
    @SerialName("seq") val seq: Int,
    @SerialName("lat") val lat: Double,
    @SerialName("lon") val lon: Double,
    @SerialName("ele") val ele: Float? = null,
    @SerialName("speed_mps") val speedMps: Float? = null,
    @SerialName("recorded_at") val recordedAtEpochMs: Long,
    @SerialName("is_pause_boundary") val isPauseBoundary: Boolean = false
)

@Serializable
data class TrackPointsChunkRequestDto(
    @SerialName("track_id") val trackId: String,
    @SerialName("chunk_seq") val chunkSeq: Int,
    @SerialName("points") val points: List<TrackPointDto>
)

/**
 * 사진 원본이나 썸네일은 절대 포함하지 않으며,
 * 다른 기기에서 "사진은 촬영한 기기(○○)에만 있어요"를 표시하기 위한 메타데이터만 전송합니다.
 */
@Serializable
data class WaypointPhotoMetaDto(
    @SerialName("has_photo") val hasPhoto: Boolean,
    @SerialName("photo_local_only") val photoLocalOnly: Boolean = true,
    @SerialName("photo_id") val photoId: String? = null,
    @SerialName("captured_device_id_hash") val capturedDeviceIdHash: String? = null,
    @SerialName("captured_device_name") val capturedDeviceName: String? = null
)

@Serializable
data class WaypointSyncDto(
    @SerialName("waypoint_id") val waypointId: String,
    @SerialName("track_id") val trackId: String,
    @SerialName("lat") val lat: Double,
    @SerialName("lon") val lon: Double,
    @SerialName("ele") val ele: Float? = null,
    @SerialName("recorded_at") val recordedAtEpochMs: Long,
    @SerialName("memo_text") val memoText: String,
    @SerialName("caption") val caption: String? = null,
    @SerialName("photo_meta") val photoMeta: WaypointPhotoMetaDto,
    @SerialName("version") val version: Int,
    @SerialName("client_updated_at") val clientUpdatedAtEpochMs: Long
)

@Serializable
data class WaypointBatchUpsertRequestDto(
    @SerialName("waypoints") val waypoints: List<WaypointSyncDto>
)

@Serializable
data class SyncAckResponseDto(
    @SerialName("status") val status: String = "ok",
    @SerialName("server_updated_at") val serverUpdatedAtEpochMs: Long = System.currentTimeMillis()
)
