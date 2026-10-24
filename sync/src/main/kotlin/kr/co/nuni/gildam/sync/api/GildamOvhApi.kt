package kr.co.nuni.gildam.sync.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * OVH 서버에 추가되는 길담 전용 REST API 인터페이스.
 * - NUNI ID Bearer 토큰으로 인증합니다.
 * - media(사진 파일/썸네일) 업로드 엔드포인트는 정책상 존재하지 않습니다.
 */
interface GildamOvhApi {

    @PUT("v1/tracks/{trackId}")
    suspend fun upsertTrackMetadata(
        @Path("trackId") trackId: String,
        @Body body: TrackUpsertRequestDto
    ): Response<SyncAckResponseDto>

    @Headers("X-Gzip-Compress: true")
    @PUT("v1/tracks/{trackId}/points")
    suspend fun uploadTrackPointsChunk(
        @Path("trackId") trackId: String,
        @Body body: TrackPointsChunkRequestDto
    ): Response<SyncAckResponseDto>

    @POST("v1/tracks/{trackId}/waypoints/batch")
    suspend fun upsertWaypointsBatch(
        @Path("trackId") trackId: String,
        @Body body: WaypointBatchUpsertRequestDto
    ): Response<SyncAckResponseDto>

    @DELETE("v1/tracks/{trackId}")
    suspend fun deleteTrack(
        @Path("trackId") trackId: String
    ): Response<SyncAckResponseDto>
}
