package kr.co.nuni.gildam.ui.screens

import androidx.compose.ui.platform.LocalContext
import kr.co.nuni.gildam.ui.l10n.*
import androidx.compose.ui.res.stringResource
import kr.co.nuni.gildam.ui.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import kr.co.nuni.gildam.recording.AutoStopPolicy

/** 설정 목록 행에 보여 줄 한 줄 요약 (예: "화면 켜 두기 꺼짐 · 최대 4시간 · 멈춤 20분") */
@androidx.compose.runtime.Composable
internal fun recordingAutoStopSummary(keepScreenOn: Boolean, maxDurationMs: Long?, idleLimitMs: Long?): String {
    val screen = if (keepScreenOn) stringResource(R.string.settings_summary_screen_on) else stringResource(R.string.settings_summary_screen_off)
    val max = if (maxDurationMs == null) stringResource(R.string.settings_summary_max_off) else stringResource(R.string.settings_summary_max, maxDurationLabel(LocalContext.current, maxDurationMs))
    val idle = if (idleLimitMs == null) stringResource(R.string.settings_summary_idle_off) else stringResource(R.string.settings_summary_idle, idleLimitLabel(LocalContext.current, idleLimitMs))
    return "$screen · $max · $idle"
}

/** 설정 > 기록 설정 > 기록 중 화면·자동 종료 (지도 강조 지표와 같은 서브 화면) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingAutoStopScreen(
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    maxDurationMs: Long?,
    onSelectMaxDurationMs: (Long?) -> Unit,
    idleLimitMs: Long?,
    onSelectIdleLimitMs: (Long?) -> Unit,
    graceMs: Long,
    onSelectGraceMs: (Long) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recording_screen_autostop_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.autostop_intro),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
            SettingsListCard {
                RecordingSettingsItems(
                    keepScreenOn, onKeepScreenOnChange,
                    maxDurationMs, onSelectMaxDurationMs,
                    idleLimitMs, onSelectIdleLimitMs,
                    graceMs, onSelectGraceMs
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * 설정 > 기록 설정 카드 안에 들어가는 '화면 켜 두기·자동 종료' 행들 (새 설정 목록 디자인).
 * - 기록 중 화면 켜 두기 (스위치, 기본 OFF)
 * - 최대 기록 시간 (2/4/6/8/10시간·사용 안 함, 기본 4시간)
 * - 오래 멈춤 기준 (10~60분 10분 단위·사용 안 함, 기본 20분)
 * - 확인 대기 시간 (5/10/15분, 기본 10분)
 * 값 선택은 하단 시트에서 합니다.
 */
@Composable
fun RecordingSettingsItems(
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    maxDurationMs: Long?,
    onSelectMaxDurationMs: (Long?) -> Unit,
    idleLimitMs: Long?,
    onSelectIdleLimitMs: (Long?) -> Unit,
    graceMs: Long,
    onSelectGraceMs: (Long) -> Unit
) {
    var sheet by remember { mutableStateOf<String?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Switch) { onKeepScreenOnChange(!keepScreenOn) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsItemIcon(Icons.Outlined.LightMode)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.keep_screen_on_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.keep_screen_on_desc),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val keepScreenOnCd = stringResource(R.string.keep_screen_on_title)
        Switch(
            checked = keepScreenOn,
            onCheckedChange = onKeepScreenOnChange,
            modifier = Modifier.semantics { contentDescription = keepScreenOnCd }
        )
    }
    SettingsItemDivider()
    SettingsListItem(
        title = stringResource(R.string.max_duration_title),
        supportingText = stringResource(R.string.max_duration_desc),
        valueText = maxDurationLabel(LocalContext.current, maxDurationMs),
        icon = Icons.Outlined.Timer,
        onClick = { sheet = "max" }
    )
    SettingsItemDivider()
    SettingsListItem(
        title = stringResource(R.string.idle_limit_title),
        supportingText = stringResource(R.string.idle_limit_desc),
        valueText = idleLimitLabel(LocalContext.current, idleLimitMs),
        icon = Icons.Outlined.PauseCircle,
        onClick = { sheet = "idle" }
    )
    SettingsItemDivider()
    SettingsListItem(
        title = stringResource(R.string.grace_title),
        supportingText = stringResource(R.string.grace_desc),
        valueText = graceLabel(LocalContext.current, graceMs),
        icon = Icons.Outlined.HourglassBottom,
        onClick = { sheet = "grace" }
    )

    val ctx = LocalContext.current
    when (sheet) {
        "max" -> OptionSheet(stringResource(R.string.max_duration_title), AutoStopPolicy.MAX_DURATION_OPTIONS_MS,
            { ms: Long? -> maxDurationLabel(ctx, ms) }, maxDurationMs, onSelectMaxDurationMs) { sheet = null }
        "idle" -> OptionSheet(stringResource(R.string.idle_limit_title), AutoStopPolicy.IDLE_LIMIT_OPTIONS_MS,
            { ms: Long? -> idleLimitLabel(ctx, ms) }, idleLimitMs, onSelectIdleLimitMs) { sheet = null }
        "grace" -> OptionSheet(stringResource(R.string.grace_title), AutoStopPolicy.GRACE_OPTIONS_MS,
            { ms: Long -> graceLabel(ctx, ms) }, graceMs, onSelectGraceMs) { sheet = null }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> OptionSheet(
    title: String,
    options: List<T>,
    label: (T) -> String,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            options.forEach { option ->
                val isSel = option == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.RadioButton) { onSelect(option); onDismiss() }
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSel) Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.state_selected),
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
