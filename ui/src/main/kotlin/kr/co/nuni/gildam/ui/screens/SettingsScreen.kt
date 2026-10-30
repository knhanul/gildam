package kr.co.nuni.gildam.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.co.nuni.gildam.auth.NuniSessionState
import kr.co.nuni.gildam.sync.SyncUiStatus
import kr.co.nuni.gildam.ui.theme.DarkModePref
import kr.co.nuni.gildam.ui.theme.GildamPalette
import kr.co.nuni.gildam.ui.theme.gildamColorScheme

/**
 * 설정 첫 화면.
 * 순서: 1) 계정·동기화 카드  2) 기록 설정  3) 데이터 보관  4) 화면 설정
 * 세부 설명과 선택지는 각 항목을 눌렀을 때 열리는 상세 화면/시트에서 보여 줍니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sessionState: NuniSessionState,
    syncStatus: SyncUiStatus,
    lastSyncSuccessAtMs: Long,
    serverTrackCount: Int?,
    onKakaoLogin: () -> Unit,
    onSwitchAccount: () -> Unit,
    /** 디버그 빌드에서만 전달 (릴리스는 null → 버튼 숨김) */
    onDemoSimulateLogin: (() -> Unit)?,
    onManualSync: () -> Unit,
    onLogout: () -> Unit,
    onNavigateBack: () -> Unit,
    /** 하단 탭바가 있을 때는 뒤로 아이콘을 숨김 */
    isTopLevelTab: Boolean = false,
    palette: GildamPalette = GildamPalette.DEFAULT,
    darkMode: DarkModePref = DarkModePref.SYSTEM,
    onSelectPalette: (GildamPalette) -> Unit = {},
    onSelectDarkMode: (DarkModePref) -> Unit = {},
    onOpenRecordingStatus: () -> Unit = {},
    onOpenGaugeEditor: () -> Unit = {},
    onOpenPhotoStorage: () -> Unit = {},
    keepScreenOn: Boolean = kr.co.nuni.gildam.data.prefs.RecordingPrefs.DEFAULT_KEEP_SCREEN_ON,
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    maxDurationMs: Long? = kr.co.nuni.gildam.recording.AutoStopPolicy.DEFAULT_MAX_DURATION_MS,
    onSelectMaxDurationMs: (Long?) -> Unit = {},
    idleLimitMs: Long? = kr.co.nuni.gildam.recording.AutoStopPolicy.DEFAULT_IDLE_LIMIT_MS,
    onSelectIdleLimitMs: (Long?) -> Unit = {},
    graceMs: Long = kr.co.nuni.gildam.recording.AutoStopPolicy.DEFAULT_GRACE_MS,
    onSelectGraceMs: (Long) -> Unit = {}
) {
    var showThemeSheet by remember { mutableStateOf(false) }
    var showModeSheet by remember { mutableStateOf(false) }
    // 시스템 설정을 다녀와도 ON_RESUME 시점에 권한·배터리 상태를 다시 조회합니다.
    val screenOffStatus = rememberScreenOffStatus()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    if (!isTopLevelTab) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // 1. 계정 · 동기화 (최상단 간결한 카드)
            AccountCard(
                sessionState = sessionState,
                syncStatus = syncStatus,
                lastSyncSuccessAtMs = lastSyncSuccessAtMs,
                serverTrackCount = serverTrackCount,
                onKakaoLogin = onKakaoLogin,
                onSwitchAccount = onSwitchAccount,
                onManualSync = onManualSync,
                onLogout = onLogout,
                onOpenPhotoStorage = onOpenPhotoStorage,
                onDemoSimulateLogin = onDemoSimulateLogin
            )

            Spacer(Modifier.height(16.dp))

            // 2. 기록 설정
            SettingsGroupLabel("기록 설정")
            SettingsListCard {
                SettingsListItem(
                    title = "화면 꺼짐 중 기록",
                    supportingText = "위치 권한과 배터리 설정을 확인해요.",
                    valueText = screenOffStatus.labelKo,
                    valueEmphasis = screenOffStatus.warn,
                    icon = Icons.Outlined.NightsStay,
                    onClick = onOpenRecordingStatus
                )
                SettingsItemDivider()
                SettingsListItem(
                    title = "지도 강조 지표",
                    supportingText = "활동별로 지도에 크게 표시할 값을 선택해요.",
                    icon = Icons.Outlined.Map,
                    onClick = onOpenGaugeEditor
                )
                SettingsItemDivider()
                RecordingSettingsItems(
                    keepScreenOn = keepScreenOn,
                    onKeepScreenOnChange = onKeepScreenOnChange,
                    maxDurationMs = maxDurationMs,
                    onSelectMaxDurationMs = onSelectMaxDurationMs,
                    idleLimitMs = idleLimitMs,
                    onSelectIdleLimitMs = onSelectIdleLimitMs,
                    graceMs = graceMs,
                    onSelectGraceMs = onSelectGraceMs
                )
            }

            Spacer(Modifier.height(16.dp))

            // 3. 데이터 보관
            SettingsGroupLabel("데이터 보관")
            SettingsListCard {
                SettingsListItem(
                    title = "사진 보관 안내",
                    supportingText = "사진 파일은 길담에서 동기화하지 않아요.",
                    icon = Icons.Outlined.PhotoLibrary,
                    onClick = onOpenPhotoStorage
                )
            }

            Spacer(Modifier.height(16.dp))

            // 4. 화면 설정
            SettingsGroupLabel("화면 설정")
            SettingsListCard {
                SettingsListItem(
                    title = "테마 색",
                    icon = Icons.Outlined.Palette,
                    valueText = palette.labelKo,
                    trailingContent = { PaletteDots(palette) },
                    onClick = { showThemeSheet = true }
                )
                SettingsItemDivider()
                SettingsListItem(
                    title = "화면 모드",
                    icon = Icons.Outlined.DarkMode,
                    valueText = darkMode.labelKo,
                    onClick = { showModeSheet = true }
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showThemeSheet) {
        ModalBottomSheet(onDismissRequest = { showThemeSheet = false }) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("테마 색", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                ThemePickerSheetContent(palette = palette, onSelectPalette = {
                    onSelectPalette(it)
                    showThemeSheet = false
                })
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (showModeSheet) {
        ModalBottomSheet(onDismissRequest = { showModeSheet = false }) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("화면 모드", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                DarkModeSheetContent(darkMode = darkMode, onSelectDarkMode = {
                    onSelectDarkMode(it)
                    showModeSheet = false
                })
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 테마 색 목록 행 오른쪽의 작은 색상 미리보기 (기본·보조·강조 3색 점) */
@Composable
private fun PaletteDots(palette: GildamPalette) {
    val cs = gildamColorScheme(palette, dark = false)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(cs.primary, cs.secondary, cs.tertiary).forEach { c ->
            Box(Modifier.size(14.dp).clip(CircleShape).background(c))
        }
    }
    Spacer(Modifier.width(6.dp))
}
