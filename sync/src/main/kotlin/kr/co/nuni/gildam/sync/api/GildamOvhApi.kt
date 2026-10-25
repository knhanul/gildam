package kr.co.nuni.gildam.sync.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 길담 동기화 API (knhanul/gildam-api). NUNI ID access token 을 Bearer 로 보냅니다.
 * 사진·미디어 업로드 엔드포인트는 정책상 없습니다.
 */
interface GildamOvhApi {
    @GET("v1/me")
    suspend fun getMe(): Response<MeResponseDto>

    /** since=0 은 살아있는 트랙만, since>0 은 삭제 표시(tombstone) 포함. limit 최대 200 */
    @GET("v1/tracks")
    suspend fun listTracks(@Query("since") since: Long, @Query("limit") limit: Int = 50): Response<TrackListResponseDto>

    @GET("v1/tracks/{id}")
    suspend fun getTrack(@Path("id") id: String): Response<TrackDto>

    /** 생성 201 / 갱신 200 / 서버가 더 최신이면 409 {current} */
    @PUT("v1/tracks/{id}")
    suspend fun putTrack(@Path("id") id: String, @Body body: TrackDto): Response<TrackDto>

    /** 200 tombstone / 없으면 204 */
    @DELETE("v1/tracks/{id}")
    suspend fun deleteTrack(@Path("id") id: String, @Query("updated_at") updatedAt: Long?): Response<TrackDto>
}
