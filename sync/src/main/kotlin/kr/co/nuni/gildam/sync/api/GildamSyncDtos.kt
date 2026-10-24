package kr.co.nuni.gildam.sync.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * gildam-api (https://www.nuni.co.kr/gildam-api) 트랙 JSON. 시각은 모두 epoch ms.
 * 사진·미디어 필드(uri/url/path/image/base64)는 서버가 400 으로 거부하므로 절대 넣지 않습니다.
 */
@Serializable
data class TrackDto(
    val id: String? = null,
    @SerialName("updated_at") val updatedAt: Long,
    val title: String? = null,
    val memo: String? = null,
    @SerialName("started_at") val startedAt: Long? = null,
    @SerialName("ended_at") val endedAt: Long? = null,
    @SerialName("distance_m") val distanceM: Double? = null,
    @SerialName("duration_s") val durationS: Double? = null,
    @SerialName("elevation_gain_m") val elevationGainM: Double? = null,
    val points: List<TrackPointDto> = emptyList(),
    val waypoints: List<WaypointDto> = emptyList(),
    /** 응답 전용 */
    val deleted: Boolean? = null,
    @SerialName("server_seq") val serverSeq: Long? = null
)

@Serializable
data class TrackPointDto(
    val lat: Double,
    val lon: Double,
    val t: Long,
    val ele: Double? = null,
    val acc: Double? = null,
    val spd: Double? = null
)

@Serializable
data class WaypointDto(
    val id: String,
    val lat: Double,
    val lon: Double,
    val t: Long? = null,
    val title: String? = null,
    val memo: String? = null,
    @SerialName("photo_local_only") val photoLocalOnly: Boolean = false,
    @SerialName("photo_count") val photoCount: Int = 0
)

@Serializable
data class TrackListResponseDto(
    val items: List<TrackDto> = emptyList(),
    val cursor: Long = 0,
    @SerialName("has_more") val hasMore: Boolean = false
)

@Serializable
data class MeResponseDto(
    val sub: String,
    @SerialName("brand_id") val brandId: String? = null,
    @SerialName("brand_role") val brandRole: String? = null,
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("track_count") val trackCount: Int = 0,
    val cursor: Long = 0
)

@Serializable
data class ConflictResponseDto(
    val error: String? = null,
    val current: TrackDto? = null
)
