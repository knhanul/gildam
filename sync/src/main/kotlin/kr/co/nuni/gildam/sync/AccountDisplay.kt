package kr.co.nuni.gildam.sync

import kr.co.nuni.gildam.sync.api.MeResponseDto

/** UI에 필요한 공개 계정 표시값만 보유하며 내부 계정 식별자(sub)는 포함하지 않습니다. */
data class AccountDisplayInfo(
    val displayName: String,
    val nickname: String?,
    val trackCount: Int?
) {
    companion object {
        val DEFAULT = AccountDisplayInfo(
            displayName = AccountDisplay.FALLBACK_DISPLAY_NAME,
            nickname = null,
            trackCount = null
        )
    }
}

/** 서버 응답을 화면에 표시할 계정 정보로 안전하게 바꾸는 순수 함수. */
object AccountDisplay {
    const val FALLBACK_DISPLAY_NAME = "누니 회원"

    fun from(dto: MeResponseDto): AccountDisplayInfo {
        val nickname = dto.nickname
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val displayName = dto.displayName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: FALLBACK_DISPLAY_NAME
        return AccountDisplayInfo(
            displayName = displayName,
            nickname = nickname,
            trackCount = dto.trackCount
        )
    }
}
