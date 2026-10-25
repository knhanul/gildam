package kr.co.nuni.gildam.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 화면에 보여줄 세션 상태. 이메일 등 개인정보는 받지 않고 NUNI 회원 ID(sub)만 씁니다. */
data class NuniSessionState(
    val isLoggedIn: Boolean = false,
    val nuniUserId: String? = null,
    val requiresReauthBanner: Boolean = false
)

/**
 * Android Keystore (AES256_GCM MasterKey) 기반 EncryptedSharedPreferences 토큰 저장소.
 * backup_rules.xml / data_extraction_rules.xml 에서 이 파일(nuni_auth_secure_prefs)은 백업 제외됩니다.
 * 진행 중인 로그인 요청(state·nonce·code_verifier)도 프로세스가 종료돼도 이어지도록 여기에 암호화 저장합니다.
 */
@Singleton
class KeystoreTokenStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _sessionFlow = MutableStateFlow(readSessionState())
    val sessionFlow: StateFlow<NuniSessionState> = _sessionFlow.asStateFlow()

    /** 로그인/갱신 결과 저장. refresh 회전: 새 refresh_token 이 오면 이전 것을 교체합니다. */
    @Synchronized
    fun saveTokens(
        accessToken: String,
        accessExpiresAtEpochMs: Long,
        refreshToken: String?,
        idToken: String?,
        nuniUserId: String
    ) {
        val e = prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_ACCESS_EXPIRES_AT, accessExpiresAtEpochMs)
            .putString(KEY_USER_ID, nuniUserId)
            .putBoolean(KEY_REQUIRES_REAUTH, false)
        if (refreshToken != null) e.putString(KEY_REFRESH_TOKEN, refreshToken)
        if (idToken != null) e.putString(KEY_ID_TOKEN, idToken)
        e.commit()
        _sessionFlow.value = readSessionState()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun getAccessExpiresAt(): Long = prefs.getLong(KEY_ACCESS_EXPIRES_AT, 0L)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)
    fun getIdToken(): String? = prefs.getString(KEY_ID_TOKEN, null)
    fun getNuniUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun savePendingAuth(session: PkceSession) {
        prefs.edit()
            .putString(KEY_PENDING_STATE, session.state)
            .putString(KEY_PENDING_NONCE, session.nonce)
            .putString(KEY_PENDING_VERIFIER, session.codeVerifier)
            .commit()
    }

    fun getPendingAuth(): PkceSession? {
        val state = prefs.getString(KEY_PENDING_STATE, null) ?: return null
        val nonce = prefs.getString(KEY_PENDING_NONCE, null) ?: return null
        val verifier = prefs.getString(KEY_PENDING_VERIFIER, null) ?: return null
        return PkceSession(state, nonce, verifier, PkceGenerator.challengeFor(verifier))
    }

    fun clearPendingAuth() {
        prefs.edit().remove(KEY_PENDING_STATE).remove(KEY_PENDING_NONCE).remove(KEY_PENDING_VERIFIER).commit()
    }

    fun markReauthRequired() {
        prefs.edit().putBoolean(KEY_REQUIRES_REAUTH, true).apply()
        _sessionFlow.value = readSessionState()
    }

    fun clearSession() {
        prefs.edit().clear().commit()
        _sessionFlow.value = NuniSessionState(isLoggedIn = false)
    }

    private fun readSessionState(): NuniSessionState {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        val uid = prefs.getString(KEY_USER_ID, null)
        return NuniSessionState(
            isLoggedIn = !token.isNullOrBlank() && !uid.isNullOrBlank(),
            nuniUserId = uid,
            requiresReauthBanner = prefs.getBoolean(KEY_REQUIRES_REAUTH, false)
        )
    }

    companion object {
        const val PREFS_FILE_NAME = "nuni_auth_secure_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_ACCESS_EXPIRES_AT = "access_expires_at"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ID_TOKEN = "id_token"
        private const val KEY_USER_ID = "nuni_user_id"
        private const val KEY_REQUIRES_REAUTH = "requires_reauth"
        private const val KEY_PENDING_STATE = "pending_state"
        private const val KEY_PENDING_NONCE = "pending_nonce"
        private const val KEY_PENDING_VERIFIER = "pending_code_verifier"
    }
}
