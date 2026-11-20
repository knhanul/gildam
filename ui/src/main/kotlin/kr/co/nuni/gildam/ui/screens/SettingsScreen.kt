package kr.co.nuni.gildam.ui.screens

import kr.co.nuni.gildam.ui.l10n.*
import androidx.compose.ui.res.stringResource
import kr.co.nuni.gildam.ui.R
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
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * 순서: 1) 계정·동기화 카드  2) 기록 설정  3) 데이터 보관  4) 화면 설정  5) 앱 정보
 * 세부 설명과 선택지는 각 항목을 눌렀을 때 열리는 상세 화면/시트에서 보여 줍니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sessionState: NuniSessionState,
    syncStatus: SyncUiStatus,
    lastSyncSuccessAtMs: Long,
    serverTrackCount: Int?,
    nickname: String? = null,
    onUpdateNickname: (String) -> Boolean = { true },
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
    /** 설정 > 계기판 구성 (통합된 화면) */
    onOpenGaugeLayout: () -> Unit = {},
    onOpenPhotoStorage: () -> Unit = {},
    keepScreenOn: Boolean = kr.co.nuni.gildam.data.prefs.RecordingPrefs.DEFAULT_KEEP_SCREEN_ON,
    maxDurationMs: Long? = kr.co.nuni.gildam.recording.AutoStopPolicy.DEFAULT_MAX_DURATION_MS,
    idleLimitMs: Long? = kr.co.nuni.gildam.recording.AutoStopPolicy.DEFAULT_IDLE_LIMIT_MS,
    graceMs: Long = kr.co.nuni.gildam.recording.AutoStopPolicy.DEFAULT_GRACE_MS,
    onOpenRecordingAutoStop: () -> Unit = {},
    hideShortRecords: Boolean = false,
    onHideShortRecordsChange: (Boolean) -> Unit = {},
    /** 설정 > 앱 정보 > 앱 종료 기록 */
    onOpenExitLog: () -> Unit = {}
) {
    var showThemeSheet by remember { mutableStateOf(false) }
    var showModeSheet by remember { mutableStateOf(false) }
    // 시스템 설정을 다녀와도 ON_RESUME 시점에 권한·배터리 상태를 다시 조회합니다.
    val screenOffStatus = rememberScreenOffStatus()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    if (!isTopLevelTab) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                nickname = nickname,
                onUpdateNickname = onUpdateNickname,
                onKakaoLogin = onKakaoLogin,
                onSwitchAccount = onSwitchAccount,
                onManualSync = onManualSync,
                onLogout = onLogout,
                onOpenPhotoStorage = onOpenPhotoStorage,
                onDemoSimulateLogin = onDemoSimulateLogin
            )

            Spacer(Modifier.height(16.dp))

            // 2. 기록 설정
            SettingsGroupLabel(stringResource(R.string.settings_group_recording))
            SettingsListCard {
                SettingsListItem(
                    title = stringResource(R.string.settings_screen_off_title),
                    supportingText = stringResource(R.string.settings_screen_off_desc),
                    valueText = screenOffStatus.label,
                    valueEmphasis = screenOffStatus.warn,
                    icon = Icons.Outlined.NightsStay,
                    onClick = onOpenRecordingStatus
                )
                SettingsItemDivider()
                SettingsListItem(
                    title = stringResource(R.string.recording_screen_autostop_title),
                    supportingText = recordingAutoStopSummary(keepScreenOn, maxDurationMs, idleLimitMs),
                    icon = Icons.Outlined.Timer,
                    onClick = onOpenRecordingAutoStop
                )
                SettingsItemDivider()
                SettingsListItem(
                    title = stringResource(R.string.gauge_layout_title),
                    supportingText = stringResource(R.string.settings_gauge_layout_desc),
                    icon = Icons.Outlined.GridView,
                    onClick = onOpenGaugeLayout
                )
            }

            Spacer(Modifier.height(16.dp))

            // 3. 데이터 보관
            SettingsGroupLabel(stringResource(R.string.settings_group_data))
            SettingsListCard {
                SettingsListItem(
                    title = stringResource(R.string.photo_storage_title),
                    supportingText = stringResource(R.string.settings_photo_desc),
                    icon = Icons.Outlined.PhotoLibrary,
                    onClick = onOpenPhotoStorage
                )
                SettingsItemDivider()
                val hideShortTitle = stringResource(R.string.hide_short_records_title)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Switch) { onHideShortRecordsChange(!hideShortRecords) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsItemIcon(Icons.Outlined.FilterAlt)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = hideShortTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.hide_short_records_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = hideShortRecords,
                        onCheckedChange = onHideShortRecordsChange,
                        modifier = Modifier.semantics { contentDescription = hideShortTitle }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 4. 화면 설정
            SettingsGroupLabel(stringResource(R.string.settings_group_display))
            SettingsListCard {
                SettingsListItem(
                    title = stringResource(R.string.settings_theme),
                    icon = Icons.Outlined.Palette,
                    valueText = palette.label,
                    trailingContent = { PaletteDots(palette) },
                    onClick = { showThemeSheet = true }
                )
                SettingsItemDivider()
                SettingsListItem(
                    title = stringResource(R.string.settings_dark_mode),
                    icon = Icons.Outlined.DarkMode,
                    valueText = darkMode.label,
                    onClick = { showModeSheet = true }
                )
            }

            Spacer(Modifier.height(16.dp))

            // 5. 앱 정보
            SettingsGroupLabel(stringResource(R.string.settings_group_app_info))
            SettingsListCard {
                SettingsListItem(
                    title = stringResource(R.string.exit_log_title),
                    supportingText = stringResource(R.string.exit_log_desc),
                    icon = Icons.Outlined.BugReport,
                    onClick = onOpenExitLog
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showThemeSheet) {
        ModalBottomSheet(onDismissRequest = { showThemeSheet = false }) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                Text(stringResource(R.string.settings_dark_mode), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
