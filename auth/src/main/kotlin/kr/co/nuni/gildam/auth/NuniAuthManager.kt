package kr.co.nuni.gildam.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NUNI ID (https://id.nuni.co.kr) Custom Tabs OAuth 2.0 + PKCE 매니저.
 * - 새 회원 시스템을 만들지 않고 NUNI ID 통합 로그인(카카오 로그인도 NUNI ID 웹 내부에서 처리)을 호출합니다.
 */
@Singleton
class NuniAuthManager @Inject constructor(
    private val tokenStore: KeystoreTokenStore
) {
    private var activePkceSession: PkceSession? = null

    /**
     * Chrome Custom Tabs를 통해 https://id.nuni.co.kr 로그인 페이지를 실행합니다.
     */
    fun launchNuniIdCustomTabLogin(activityContext: Context) {
        val pkce = PkceGenerator.generateSession()
        activePkceSession = pkce

        val authorizeUri = Uri.parse(NuniAuthConfig.AUTHORIZE_ENDPOINT)
            .buildUpon()
            .appendQueryParameter("client_id", NuniAuthConfig.CLIENT_ID)
            .appendQueryParameter("redirect_uri", NuniAuthConfig.REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", NuniAuthConfig.DEFAULT_SCOPE)
            .appendQueryParameter("code_challenge", pkce.codeChallenge)
            .appendQueryParameter("code_challenge_method", pkce.codeChallengeMethod)
            .appendQueryParameter("state", pkce.state)
            .build()

        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()

        customTabsIntent.launchUrl(activityContext, authorizeUri)
    }

    /**
     * App Links 콜백(https://gildam.nuni.co.kr/auth/callback?code=...&state=...) 수신 시
     * state를 검증하고 code + code_verifier를 반환합니다.
     */
    fun handleRedirectCallback(callbackUri: Uri): Result<Pair<String, String>> {
        val returnedState = callbackUri.getQueryParameter("state")
        val code = callbackUri.getQueryParameter("code")
        val session = activePkceSession

        if (session == null || returnedState != session.state || code.isNullOrBlank()) {
            return Result.failure(IllegalStateException("유효하지 않은 OAuth 콜백 state 또는 인가 코드입니다."))
        }
        activePkceSession = null
        return Result.success(code to session.codeVerifier)
    }

    /**
     * 개발/스튜디오 테스트용 데모 로그인 헬퍼 (NUNI ID 서버 연동 전 로컬 UI·동기화 파이프라인 검증용)
     */
    fun simulateDemoNuniLogin() {
        tokenStore.saveTokens(
            accessToken = "demo_nuni_jwt_token_${System.currentTimeMillis()}",
            refreshToken = "demo_nuni_refresh_token",
            nuniUserId = "nuni_user_0192b8",
            userEmail = "traveler@nuni.co.kr"
        )
    }

    fun logout() {
        tokenStore.clearSession()
    }
}
