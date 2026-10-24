package kr.co.nuni.gildam.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

data class PkceSession(
    val state: String,
    val codeVerifier: String,
    val codeChallenge: String,
    val codeChallengeMethod: String = "S256"
)

object PkceGenerator {
    private val secureRandom = SecureRandom()

    fun generateSession(): PkceSession {
        val verifierBytes = ByteArray(32)
        secureRandom.nextBytes(verifierBytes)
        val codeVerifier = Base64.encodeToString(
            verifierBytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

        val digest = MessageDigest.getInstance("SHA-256")
        val challengeBytes = digest.digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        val codeChallenge = Base64.encodeToString(
            challengeBytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

        val stateBytes = ByteArray(16)
        secureRandom.nextBytes(stateBytes)
        val state = Base64.encodeToString(
            stateBytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

        return PkceSession(
            state = state,
            codeVerifier = codeVerifier,
            codeChallenge = codeChallenge
        )
    }
}
