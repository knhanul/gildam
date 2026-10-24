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

data class NuniSessionState(
    val isLoggedIn: Boolean = false,
    val nuniUserId: String? = null,
    val userDisplayEmail: String? = null,
    val requiresReauthBanner: Boolean = false
)

/**
 * Android Keystore (AES256_GCM MasterKey) 기반 토큰 보안 저장소.
 * backup_rules.xml 및 data_extraction_rules.xml 에서 이 파일(nuni_auth_secure_prefs)은 클라우드 백업 제외 처리됩니다.
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

    fun saveTokens(
        accessToken: String,
        refreshToken: String?,
        nuniUserId: String,
        userEmail: String?
    ) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_USER_ID, nuniUserId)
            .putString(KEY_USER_EMAIL, userEmail)
            .putBoolean(KEY_REQUIRES_REAUTH, false)
            .apply()
        _sessionFlow.value = readSessionState()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun getNuniUserId(): String? = prefs.getString(KEY_USER_ID, null)

    fun markReauthRequired() {
        prefs.edit().putBoolean(KEY_REQUIRES_REAUTH, true).apply()
        _sessionFlow.value = readSessionState()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        _sessionFlow.value = NuniSessionState(isLoggedIn = false)
    }

    private fun readSessionState(): NuniSessionState {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        val uid = prefs.getString(KEY_USER_ID, null)
        val email = prefs.getString(KEY_USER_EMAIL, null)
        val reauth = prefs.getBoolean(KEY_REQUIRES_REAUTH, false)
        return NuniSessionState(
            isLoggedIn = !token.isNullOrBlank() && !uid.isNullOrBlank(),
            nuniUserId = uid,
            userDisplayEmail = email,
            requiresReauthBanner = reauth
        )
    }

    companion object {
        const val PREFS_FILE_NAME = "nuni_auth_secure_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "nuni_user_id"
        private const val KEY_USER_EMAIL = "nuni_user_email"
        private const val KEY_REQUIRES_REAUTH = "requires_reauth"
    }
}
