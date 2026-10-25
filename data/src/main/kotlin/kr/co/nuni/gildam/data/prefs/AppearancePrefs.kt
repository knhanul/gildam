package kr.co.nuni.gildam.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appearanceStore by preferencesDataStore(name = "appearance")

/** 테마 색(팔레트 이름)과 밝기 모드(SYSTEM/LIGHT/DARK)를 저장. 값이 없으면 null. */
@Singleton
class AppearancePrefs @Inject constructor(@ApplicationContext private val context: Context) {
    private val paletteKey = stringPreferencesKey("palette")
    private val modeKey = stringPreferencesKey("dark_mode")

    val palette: Flow<String?> = context.appearanceStore.data.map { it[paletteKey] }
    val darkMode: Flow<String?> = context.appearanceStore.data.map { it[modeKey] }

    suspend fun setPalette(name: String) { context.appearanceStore.edit { it[paletteKey] = name } }
    suspend fun setDarkMode(name: String) { context.appearanceStore.edit { it[modeKey] = name } }
}
