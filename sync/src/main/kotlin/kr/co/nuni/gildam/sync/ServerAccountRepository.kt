package kr.co.nuni.gildam.sync

import kr.co.nuni.gildam.sync.api.GildamOvhApi
import javax.inject.Inject
import javax.inject.Singleton

/** GET /v1/me 결과를 UI 에 넘기는 얇은 저장소 (Retrofit 타입을 UI 로 노출하지 않음). */
@Singleton
class ServerAccountRepository @Inject constructor(private val api: GildamOvhApi) {
    /** 서버 응답에서 화면에 필요한 계정 정보만 가져옵니다. 실패하거나 응답이 비면 null입니다. */
    suspend fun fetchAccountInfo(): AccountDisplayInfo? =
        runCatching { api.getMe() }
            .getOrNull()
            ?.takeIf { it.isSuccessful }
            ?.body()
            ?.let(AccountDisplay::from)

    /** 기존 호출부 호환용 기록 수 조회 */
    suspend fun fetchTrackCount(): Int? = fetchAccountInfo()?.trackCount
}
