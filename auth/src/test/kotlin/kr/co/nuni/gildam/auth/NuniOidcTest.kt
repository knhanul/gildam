package kr.co.nuni.gildam.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.URLDecoder
import java.util.Base64

class NuniOidcTest {
    private val clientId = "gildam-android"
    private val pkce = PkceSession("st", "nn", "ver", PkceGenerator.challengeFor("ver"))

    private fun jwt(payload: String): String {
        val e = Base64.getUrlEncoder().withoutPadding()
        return e.encodeToString("""{"alg":"RS256"}""".toByteArray()) + "." + e.encodeToString(payload.toByteArray()) + ".sig"
    }

    @Test fun pkceChallengeMatchesRfc7636Example() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", PkceGenerator.challengeFor("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test fun generatedSessionIsRandomAndUrlSafe() {
        val a = PkceGenerator.generateSession(); val b = PkceGenerator.generateSession()
        assertTrue(a.state != b.state && a.nonce != b.nonce && a.codeVerifier != b.codeVerifier)
        assertTrue(a.codeVerifier.length >= 43 && Regex("^[A-Za-z0-9_-]+$").matches(a.codeVerifier))
        assertEquals(PkceGenerator.challengeFor(a.codeVerifier), a.codeChallenge)
    }

    @Test fun authorizeHasKakaoHintNonceStateAndNoPromptByDefault() {
        val m = NuniOidc.authorizeParams(pkce, clientId, forceLogin = false).toMap()
        assertEquals("kakao", m["idp_hint"]); assertEquals("nn", m["nonce"]); assertEquals("st", m["state"])
        assertEquals("S256", m["code_challenge_method"]); assertEquals("openid", m["scope"])
        assertEquals("kr.co.nuni.gildam:/oauthredirect", m["redirect_uri"]); assertEquals("code", m["response_type"])
        assertFalse(m.containsKey("prompt"))
    }

    @Test fun switchAccountAddsPromptLogin() {
        assertEquals("login", NuniOidc.authorizeParams(pkce, clientId, forceLogin = true).toMap()["prompt"])
    }

    @Test fun urlIsEncoded() {
        val url = NuniOidc.buildUrl(NuniAuthConfig.AUTHORIZE_ENDPOINT, NuniOidc.authorizeParams(pkce, clientId, false))
        assertTrue(url.startsWith("https://id.nuni.co.kr/oauth/authorize?"))
        assertTrue(url.contains("redirect_uri=kr.co.nuni.gildam%3A%2Foauthredirect"))
        assertEquals("kr.co.nuni.gildam:/oauthredirect", URLDecoder.decode(url.substringAfter("redirect_uri=").substringBefore("&"), "UTF-8"))
    }

    private fun cb(vararg p: Pair<String, String>) = p.toMap()::get

    @Test fun callbackOk() {
        val r = NuniOidc.validateCallback(cb("code" to "c1", "state" to "st", "iss" to "https://id.nuni.co.kr"), "st")
        assertEquals(NuniOidc.CallbackResult.Code("c1"), r)
    }

    @Test fun callbackRejectsStateMismatchAndMissingPending() {
        assertEquals("state_mismatch", (NuniOidc.validateCallback(cb("code" to "c", "state" to "x", "iss" to NuniAuthConfig.ISSUER), "st") as NuniOidc.CallbackResult.Error).error)
        assertEquals("state_mismatch", (NuniOidc.validateCallback(cb("code" to "c", "state" to "st", "iss" to NuniAuthConfig.ISSUER), null) as NuniOidc.CallbackResult.Error).error)
    }

    @Test fun callbackRejectsWrongOrMissingIss() {
        assertEquals("iss_mismatch", (NuniOidc.validateCallback(cb("code" to "c", "state" to "st", "iss" to "https://evil"), "st") as NuniOidc.CallbackResult.Error).error)
        assertEquals("iss_missing", (NuniOidc.validateCallback(cb("code" to "c", "state" to "st"), "st") as NuniOidc.CallbackResult.Error).error)
    }

    @Test fun callbackPassesServerError() {
        val r = NuniOidc.validateCallback(cb("error" to "access_denied", "state" to "st", "iss" to NuniAuthConfig.ISSUER), "st")
        assertEquals("access_denied", (r as NuniOidc.CallbackResult.Error).error)
    }

    @Test fun parsesTokenResponse() {
        val t = NuniOidc.parseTokenResponse("""{"access_token":"a","token_type":"Bearer","expires_in":900,"id_token":"i","refresh_token":"r","scope":"openid","extra":1}""")
        assertEquals("a", t.accessToken); assertEquals("r", t.refreshToken); assertEquals(900, t.expiresIn)
    }

    private val now = 1_800_000_000L
    private fun idt(aud: String = "\"gildam-android\"", nonce: String = "nn", iss: String = NuniAuthConfig.ISSUER, exp: Long = now + 600, extra: String = "") =
        jwt("""{"iss":"$iss","sub":"u-123","aud":$aud,"nonce":"$nonce","exp":$exp,"iat":$now,"brand_id":"gildam"$extra}""")

    @Test fun idTokenValid() {
        val c = NuniOidc.validateIdToken(idt(), clientId, "nn", now)
        assertEquals("u-123", c.sub); assertEquals("gildam", c.brandId)
    }

    private fun expectFail(block: () -> Unit) { try { block(); fail("should fail") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {} }

    @Test fun idTokenRejectsBadNonceAudIssExp() {
        expectFail { NuniOidc.validateIdToken(idt(nonce = "other"), clientId, "nn", now) }
        expectFail { NuniOidc.validateIdToken(idt(aud = "\"other-app\""), clientId, "nn", now) }
        expectFail { NuniOidc.validateIdToken(idt(iss = "https://evil"), clientId, "nn", now) }
        expectFail { NuniOidc.validateIdToken(idt(exp = now - 3600), clientId, "nn", now) }
        expectFail { NuniOidc.validateIdToken("not-a-jwt", clientId, "nn", now) }
    }

    @Test fun idTokenAudArrayNeedsAzp() {
        expectFail { NuniOidc.validateIdToken(idt(aud = "[\"gildam-android\",\"x\"]"), clientId, "nn", now) }
        NuniOidc.validateIdToken(idt(aud = "[\"gildam-android\",\"x\"]", extra = ",\"azp\":\"gildam-android\""), clientId, "nn", now)
    }
}
