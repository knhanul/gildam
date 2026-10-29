package kr.co.nuni.gildam.auth

/**
 * NUNI ID (https://id.nuni.co.kr) OIDC 설정.
 * 엔드포인트는 https://id.nuni.co.kr/.well-known/openid-configuration 기준.
 *
 * 사용자에게는 '카카오로 시작하기'로 보이도록 authorize 요청에 [IDP_HINT]=kakao 를 붙입니다.
 * (NUNI ID 서버가 자체 로그인 화면 없이 바로 카카오 로그인으로 보내고, 끝나면 같은 요청으로 복귀)
 */
object NuniAuthConfig {
    const val ISSUER = "https://id.nuni.co.kr"
    const val ACCOUNT_PAGE_URL = "$ISSUER/me"
    const val AUTHORIZE_ENDPOINT = "$ISSUER/oauth/authorize"
    const val TOKEN_ENDPOINT = "$ISSUER/oauth/token"
    const val REVOKE_ENDPOINT = "$ISSUER/oauth/revoke"
    const val END_SESSION_ENDPOINT = "$ISSUER/oauth/end_session"

    /** gradle property `nuniClientId` 로 주입 (기본값 gildam-android 자리표시). */
    val CLIENT_ID: String = BuildConfig.NUNI_CLIENT_ID

    /** 역도메인 커스텀 스킴 콜백. NUNI ID oauth_clients 에 정확히 같은 값으로 등록 필요. */
    const val REDIRECT_SCHEME = "kr.co.nuni.gildam"
    const val REDIRECT_URI = "$REDIRECT_SCHEME:/oauthredirect"
    const val POST_LOGOUT_REDIRECT_URI = "$REDIRECT_SCHEME:/logout"

    /** NUNI ID scopes_supported = ["openid"] */
    const val DEFAULT_SCOPE = "openid"
    const val IDP_HINT = "kakao"
}
