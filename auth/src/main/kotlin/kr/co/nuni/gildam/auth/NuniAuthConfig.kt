package kr.co.nuni.gildam.auth

/**
 * NUNI ID (https://id.nuni.co.kr) OAuth 2.0 + PKCE 설정 상수.
 *
 * [오픈 퀘스천 반영 안내]:
 * 의뢰자(nuni 팀)로부터 실제 client_id, authorize/token 엔드포인트, App Links redirect_uri가
 * 확정되면 아래 상수만 교체하여 즉시 실서버 연동이 동작하도록 분리되어 있습니다.
 */
object NuniAuthConfig {
    const val ISSUER_BASE_URL = "https://id.nuni.co.kr"

    // TODO(Open Question #1): id.nuni.co.kr 의 실제 OAuth2 인가 및 토큰 교환 경로 확정 시 교체
    const val AUTHORIZE_ENDPOINT = "$ISSUER_BASE_URL/oauth/authorize"
    const val TOKEN_ENDPOINT = "$ISSUER_BASE_URL/oauth/token"
    const val LOGOUT_ENDPOINT = "$ISSUER_BASE_URL/oauth/logout"

    // TODO(Open Question #2): 길담 Android 전용 발급 Client ID 입력
    const val CLIENT_ID = "gildam-android-client"

    // TODO(Open Question #3): Android App Links (권장) 또는 커스텀 스킴 콜백 URI 확정 시 교체
    const val REDIRECT_URI = "https://gildam.nuni.co.kr/auth/callback"

    const val DEFAULT_SCOPE = "openid profile gildam.sync"
}
