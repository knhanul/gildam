package kr.co.nuni.gildam.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NUNI ID OIDC (Authorization Code + PKCE S256, Custom Tabs) 매니저.
 * 사용자에게는 '카카오로 시작하기'로 보이며(idp_hint=kakao), 실제 계정·토큰은 NUNI ID 가 발급합니다.
 */
@Singleton
class NuniAuthManager @Inject constructor(
    private val tokenStore: KeystoreTokenStore
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val refreshMutex = Mutex()
    private val formType = "application/x-www-form-urlencoded".toMediaType()

    private val _lastError = MutableStateFlow<String?>(null)
    /** 사용자에게 보여줄 최근 로그인 오류 문구 (없으면 null). */
    val lastError: StateFlow<String?> = _lastError.asStateFlow()
    fun clearError() { _lastError.value = null }

    /**
     * 카카오 로그인(NUNI ID) 시작.
     * @param switchAccount true 면 prompt=login 으로 카카오 계정을 다시 입력받습니다(계정 전환).
     */
    fun launchLogin(activityContext: Context, switchAccount: Boolean = false) {
        val pkce = PkceGenerator.generateSession()
        tokenStore.savePendingAuth(pkce)
        val url = NuniOidc.buildUrl(
            NuniAuthConfig.AUTHORIZE_ENDPOINT,
            NuniOidc.authorizeParams(pkce, NuniAuthConfig.CLIENT_ID, forceLogin = switchAccount)
        )
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(activityContext, Uri.parse(url))
    }

    /** 누니 계정의 닉네임 및 계정 관리 화면을 Custom Tabs로 엽니다. */
    fun openAccountPage(context: Context) {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, Uri.parse(NuniAuthConfig.ACCOUNT_PAGE_URL))
    }

    fun isAuthRedirect(uri: Uri): Boolean =
        uri.scheme == NuniAuthConfig.REDIRECT_SCHEME && uri.path == "/oauthredirect"

    fun isLogoutRedirect(uri: Uri): Boolean =
        uri.scheme == NuniAuthConfig.REDIRECT_SCHEME && uri.path == "/logout"

    /** 콜백 처리: state·iss 확인 → /oauth/token 교환 → id_token(iss·aud·nonce·exp) 확인 → 암호화 저장. */
    suspend fun completeLogin(callbackUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val pending = tokenStore.getPendingAuth()
        val result = runCatching {
            when (val cb = NuniOidc.validateCallback(callbackUri::getQueryParameter, pending?.state)) {
                is NuniOidc.CallbackResult.Error -> throw AuthException(
                    if (cb.error == "access_denied") "카카오 로그인이 취소됐어요." else (cb.description ?: "로그인에 실패했어요 (${cb.error}).")
                )
                is NuniOidc.CallbackResult.Code -> {
                    val token = postToken(
                        listOf(
                            "grant_type" to "authorization_code",
                            "code" to cb.code,
                            "redirect_uri" to NuniAuthConfig.REDIRECT_URI,
                            "client_id" to NuniAuthConfig.CLIENT_ID,
                            "code_verifier" to pending!!.codeVerifier
                        )
                    )
                    val idToken = token.idToken ?: throw AuthException("id_token 이 없어요.")
                    val claims = try {
                        NuniOidc.validateIdToken(idToken, NuniAuthConfig.CLIENT_ID, pending.nonce, System.currentTimeMillis() / 1000)
                    } catch (e: IllegalArgumentException) {
                        throw AuthException("로그인 응답 검증에 실패했어요.", e)
                    }
                    save(token, claims.sub)
                }
            }
        }
        // state 가 맞지 않은(남의) 콜백이면 진행 중인 요청은 그대로 둔다
        if (pending != null && callbackUri.getQueryParameter("state") == pending.state) tokenStore.clearPendingAuth()
        result.onFailure { _lastError.value = it.message ?: "로그인에 실패했어요." }.onSuccess { _lastError.value = null }
    }

    /**
     * refresh_token 으로 access token 갱신 (서버가 refresh 를 회전하므로 새 값으로 교체 저장).
     * 동시에 여러 번 불려도 한 번만 서버에 요청합니다.
     * @return 새 access token, 재로그인이 필요하면 null
     */
    suspend fun refreshAccessToken(staleAccessToken: String? = null): String? = refreshMutex.withLock {
        withContext(Dispatchers.IO) {
            val current = tokenStore.getAccessToken()
            if (staleAccessToken != null && current != null && current != staleAccessToken) return@withContext current
            val refresh = tokenStore.getRefreshToken() ?: run { tokenStore.markReauthRequired(); return@withContext null }
            try {
                val token = postToken(
                    listOf("grant_type" to "refresh_token", "refresh_token" to refresh, "client_id" to NuniAuthConfig.CLIENT_ID)
                )
                val sub = token.idToken?.let { runCatching { NuniOidc.decodeJwtPayload(it)["sub"]?.toString()?.trim('"') }.getOrNull() }
                    ?: tokenStore.getNuniUserId() ?: return@withContext null
                save(token, sub)
                token.accessToken
            } catch (e: AuthException) {
                // invalid_grant 등: refresh 폐기/재사용 탐지 → 다시 로그인 필요
                tokenStore.markReauthRequired()
                null
            } catch (e: IOException) {
                null // 네트워크 오류: 다음에 다시 시도
            }
        }
    }

    /** OkHttp Authenticator 등 동기 코드용. */
    fun refreshAccessTokenBlocking(staleAccessToken: String?): String? = runBlocking { refreshAccessToken(staleAccessToken) }

    /** 로그아웃: refresh token 폐기(revoke) → 기기 세션 삭제 → NUNI ID end_session 으로 포털 세션 종료. */
    suspend fun logout(activityContext: Context?) {
        val refresh = tokenStore.getRefreshToken()
        val idToken = tokenStore.getIdToken()
        withContext(Dispatchers.IO) {
            if (refresh != null) runCatching {
                http.newCall(
                    Request.Builder().url(NuniAuthConfig.REVOKE_ENDPOINT)
                        .post(NuniOidc.formBody(listOf("token" to refresh, "token_type_hint" to "refresh_token", "client_id" to NuniAuthConfig.CLIENT_ID)).toRequestBody(formType))
                        .build()
                ).execute().close()
            }
        }
        tokenStore.clearSession()
        if (activityContext != null && idToken != null) {
            val url = NuniOidc.buildUrl(
                NuniAuthConfig.END_SESSION_ENDPOINT,
                listOf(
                    "id_token_hint" to idToken,
                    "client_id" to NuniAuthConfig.CLIENT_ID,
                    "post_logout_redirect_uri" to NuniAuthConfig.POST_LOGOUT_REDIRECT_URI
                )
            )
            runCatching { CustomTabsIntent.Builder().build().launchUrl(activityContext, Uri.parse(url)) }
        }
    }

    /** 개발용 데모 세션 (디버그 빌드 화면에서만 노출). 실제 서버 토큰이 아닙니다. */
    fun simulateDemoNuniLogin() {
        tokenStore.saveTokens(
            accessToken = "demo-access-${System.currentTimeMillis()}",
            accessExpiresAtEpochMs = System.currentTimeMillis() + 15 * 60_000,
            refreshToken = null,
            idToken = null,
            nuniUserId = "demo-sub-0192b8"
        )
    }

    private fun save(token: NuniOidc.TokenResponse, sub: String) {
        tokenStore.saveTokens(
            accessToken = token.accessToken,
            accessExpiresAtEpochMs = System.currentTimeMillis() + token.expiresIn * 1000,
            refreshToken = token.refreshToken,
            idToken = token.idToken,
            nuniUserId = sub
        )
    }

    private fun postToken(params: List<Pair<String, String>>): NuniOidc.TokenResponse {
        val req = Request.Builder().url(NuniAuthConfig.TOKEN_ENDPOINT)
            .header("Accept", "application/json")
            .post(NuniOidc.formBody(params).toRequestBody(formType))
            .build()
        http.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw AuthException("토큰 발급 실패 (HTTP ${res.code})")
            return NuniOidc.parseTokenResponse(body)
        }
    }
}

class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause)
