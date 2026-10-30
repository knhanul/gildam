package kr.co.nuni.gildam.sync

import kr.co.nuni.gildam.sync.api.GildamOvhApi
import javax.inject.Inject
import javax.inject.Singleton

/** GET /v1/me 결과를 UI 에 넘기는 얇은 저장소 (Retrofit 타입을 UI 로 노출하지 않음). */
@Singleton
class ServerAccountRepository @Inject constructor(private val api: GildamOvhApi) {
    /** 서버에 저장된 기록 수, 실패하면 null */
    suspend fun fetchTrackCount(): Int? = fetchAccount()?.trackCount

    /** GET /v1/me 요약 (기록 수 + NUNI ID 닉네임). 실패하면 null */
    suspend fun fetchAccount(): ServerAccount? =
        runCatching { api.getMe() }.getOrNull()?.takeIf { it.isSuccessful }?.body()
            ?.let { ServerAccount(trackCount = it.trackCount, nickname = it.nickname?.trim()?.takeIf(String::isNotEmpty)) }
}

data class ServerAccount(val trackCount: Int, val nickname: String?)
