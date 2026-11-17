package kr.co.nuni.gildam.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    private val mapModeKey = stringPreferencesKey("record_map_mode")
    private val groupModeKey = stringPreferencesKey("record_group_mode")
    private val hideShortKey = booleanPreferencesKey("hide_short_records")

    val palette: Flow<String?> = context.appearanceStore.data.map { it[paletteKey] }
    val darkMode: Flow<String?> = context.appearanceStore.data.map { it[modeKey] }

    /** 기록 중 지도 보기 기본값 (FOLLOW/OVERVIEW) */
    val recordMapMode: Flow<String?> = context.appearanceStore.data.map { it[mapModeKey] }
    suspend fun setRecordMapMode(name: String) { context.appearanceStore.edit { it[mapModeKey] = name } }

    /** 기록 보관함 묶어 보기 (RECENT/MONTH/REGION) */
    val recordGroupMode: Flow<String?> = context.appearanceStore.data.map { it[groupModeKey] }
    suspend fun setRecordGroupMode(name: String) { context.appearanceStore.edit { it[groupModeKey] = name } }

    /** 기록 보관함: 아주 짧은 기록(0.1km 미만 또는 1분 미만) 숨기기 (기본 false) */
    val hideShortRecords: Flow<Boolean> = context.appearanceStore.data.map { it[hideShortKey] ?: false }
    suspend fun setHideShortRecords(hide: Boolean) { context.appearanceStore.edit { it[hideShortKey] = hide } }

    suspend fun setPalette(name: String) { context.appearanceStore.edit { it[paletteKey] = name } }
    suspend fun setDarkMode(name: String) { context.appearanceStore.edit { it[modeKey] = name } }
}
