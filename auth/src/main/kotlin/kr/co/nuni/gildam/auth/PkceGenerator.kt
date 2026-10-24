package kr.co.nuni.gildam.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class PkceSession(
    val state: String,
    val nonce: String,
    val codeVerifier: String,
    val codeChallenge: String,
    val codeChallengeMethod: String = "S256"
)

object PkceGenerator {
    private val secureRandom = SecureRandom()
    private val b64 = Base64.getUrlEncoder().withoutPadding()

    private fun random(bytes: Int): String = ByteArray(bytes).also(secureRandom::nextBytes).let(b64::encodeToString)

    fun challengeFor(codeVerifier: String): String =
        b64.encodeToString(MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII)))

    fun generateSession(): PkceSession {
        val verifier = random(32)
        return PkceSession(state = random(16), nonce = random(16), codeVerifier = verifier, codeChallenge = challengeFor(verifier))
    }
}
