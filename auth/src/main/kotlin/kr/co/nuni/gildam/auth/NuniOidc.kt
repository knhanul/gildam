package kr.co.nuni.gildam.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder
import java.util.Base64

/** 안드로이드 의존성 없는 OIDC 순수 로직 (단위 테스트 대상). */
object NuniOidc {
    private val json = Json { ignoreUnknownKeys = true }

    /** authorize 쿼리 파라미터 (순서 유지). */
    fun authorizeParams(pkce: PkceSession, clientId: String, forceLogin: Boolean): List<Pair<String, String>> = buildList {
        add("response_type" to "code")
        add("client_id" to clientId)
        add("redirect_uri" to NuniAuthConfig.REDIRECT_URI)
        add("scope" to NuniAuthConfig.DEFAULT_SCOPE)
        add("state" to pkce.state)
        add("nonce" to pkce.nonce)
        add("code_challenge" to pkce.codeChallenge)
        add("code_challenge_method" to pkce.codeChallengeMethod)
        add("idp_hint" to NuniAuthConfig.IDP_HINT)
        if (forceLogin) add("prompt" to "login")
    }

    fun buildUrl(base: String, params: List<Pair<String, String>>): String =
        base + "?" + params.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }

    fun formBody(params: List<Pair<String, String>>): String = params.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    sealed interface CallbackResult {
        data class Code(val code: String) : CallbackResult
        data class Error(val error: String, val description: String?) : CallbackResult
    }

    /** 콜백 쿼리 검증: state 일치, iss(RFC 9207) 일치, error 처리. */
    fun validateCallback(query: (String) -> String?, expectedState: String?): CallbackResult {
        val state = query("state")
        if (expectedState == null || state != expectedState) return CallbackResult.Error("state_mismatch", "로그인 요청 정보가 맞지 않아요. 다시 시도해 주세요.")
        val iss = query("iss")
        if (iss != null && iss != NuniAuthConfig.ISSUER) return CallbackResult.Error("iss_mismatch", "알 수 없는 로그인 서버 응답이에요.")
        query("error")?.let { return CallbackResult.Error(it, query("error_description")) }
        if (iss == null) return CallbackResult.Error("iss_missing", "로그인 서버 응답에 발급자 정보가 없어요.")
        val code = query("code")
        if (code.isNullOrBlank()) return CallbackResult.Error("missing_code", "인가 코드가 없어요.")
        return CallbackResult.Code(code)
    }

    @Serializable
    data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("token_type") val tokenType: String = "Bearer",
        @SerialName("expires_in") val expiresIn: Long = 900,
        @SerialName("id_token") val idToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        val scope: String? = null
    )

    fun parseTokenResponse(body: String): TokenResponse = json.decodeFromString(TokenResponse.serializer(), body)

    fun decodeJwtPayload(jwt: String): JsonObject {
        val parts = jwt.split(".")
        require(parts.size == 3) { "JWT 형식이 아니에요" }
        return json.parseToJsonElement(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)).jsonObject
    }

    data class IdTokenClaims(val sub: String, val brandId: String?)

    /**
     * id_token 클레임 검증 (토큰 엔드포인트에서 TLS 로 직접 받은 토큰이므로 OIDC Core 3.1.3.7 에 따라 서명 검증은 생략 가능).
     * iss, aud(또는 azp), nonce, exp 를 확인합니다.
     */
    fun validateIdToken(idToken: String, clientId: String, expectedNonce: String?, nowEpochSec: Long, skewSec: Long = 120): IdTokenClaims {
        val c = decodeJwtPayload(idToken)
        fun str(k: String) = (c[k] as? JsonPrimitive)?.contentOrNull
        require(str("iss") == NuniAuthConfig.ISSUER) { "id_token iss 불일치" }
        val aud = when (val a = c["aud"]) {
            is JsonPrimitive -> listOfNotNull(a.contentOrNull)
            is JsonArray -> a.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            else -> emptyList()
        }
        require(clientId in aud) { "id_token aud 불일치" }
        if (aud.size > 1) require(str("azp") == clientId) { "id_token azp 불일치" }
        if (expectedNonce != null) require(str("nonce") == expectedNonce) { "id_token nonce 불일치" }
        val exp = (c["exp"] as? JsonPrimitive)?.longOrNull ?: error("id_token exp 없음")
        require(exp + skewSec > nowEpochSec) { "id_token 만료" }
        val sub = str("sub")
        require(!sub.isNullOrBlank()) { "id_token sub 없음" }
        return IdTokenClaims(sub = sub, brandId = str("brand_id"))
    }
}
