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
    private val mapModeKey = stringPreferencesKey("record_map_mode")
    private val listLayoutKey = stringPreferencesKey("record_list_layout")
    private val groupModeKey = stringPreferencesKey("record_group_mode")

    val palette: Flow<String?> = context.appearanceStore.data.map { it[paletteKey] }
    val darkMode: Flow<String?> = context.appearanceStore.data.map { it[modeKey] }

    /** 기록 중 지도 보기 기본값 (FOLLOW/OVERVIEW) */
    val recordMapMode: Flow<String?> = context.appearanceStore.data.map { it[mapModeKey] }
    suspend fun setRecordMapMode(name: String) { context.appearanceStore.edit { it[mapModeKey] = name } }

    /** 기록 보관함 보기 방식 (CARD/LIST) */
    val recordListLayout: Flow<String?> = context.appearanceStore.data.map { it[listLayoutKey] }
    suspend fun setRecordListLayout(name: String) { context.appearanceStore.edit { it[listLayoutKey] = name } }

    /** 기록 보관함 묶어 보기 (RECENT/MONTH/REGION) */
    val recordGroupMode: Flow<String?> = context.appearanceStore.data.map { it[groupModeKey] }
    suspend fun setRecordGroupMode(name: String) { context.appearanceStore.edit { it[groupModeKey] = name } }

    suspend fun setPalette(name: String) { context.appearanceStore.edit { it[paletteKey] = name } }
    suspend fun setDarkMode(name: String) { context.appearanceStore.edit { it[modeKey] = name } }
}
