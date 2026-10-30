package kr.co.nuni.gildam.ui.screens

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
import kr.co.nuni.gildam.recording.AutoStopPolicy

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
                text = "기록 중 화면 켜 두기",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "기록 화면을 보는 동안 화면이 꺼지지 않아요.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = keepScreenOn,
            onCheckedChange = onKeepScreenOnChange,
            modifier = Modifier.semantics { contentDescription = "기록 중 화면 켜 두기" }
        )
    }
    SettingsItemDivider()
    SettingsListItem(
        title = "최대 기록 시간",
        supportingText = "이 시간이 지나면 알림으로 묻고 기록을 마쳐요.",
        valueText = AutoStopPolicy.formatMaxDurationLabel(maxDurationMs),
        icon = Icons.Outlined.Timer,
        onClick = { sheet = "max" }
    )
    SettingsItemDivider()
    SettingsListItem(
        title = "오래 멈춤 기준",
        supportingText = "오래 움직이지 않으면 알림으로 물어봐요.",
        valueText = AutoStopPolicy.formatIdleLimitLabel(idleLimitMs),
        icon = Icons.Outlined.PauseCircle,
        onClick = { sheet = "idle" }
    )
    SettingsItemDivider()
    SettingsListItem(
        title = "확인 대기 시간",
        supportingText = "알림에 답이 없으면 이 시간 뒤 기록을 마쳐요.",
        valueText = AutoStopPolicy.formatGraceLabel(graceMs),
        icon = Icons.Outlined.HourglassBottom,
        onClick = { sheet = "grace" }
    )

    when (sheet) {
        "max" -> OptionSheet("최대 기록 시간", AutoStopPolicy.MAX_DURATION_OPTIONS_MS,
            AutoStopPolicy::formatMaxDurationLabel, maxDurationMs, onSelectMaxDurationMs) { sheet = null }
        "idle" -> OptionSheet("오래 멈춤 기준", AutoStopPolicy.IDLE_LIMIT_OPTIONS_MS,
            AutoStopPolicy::formatIdleLimitLabel, idleLimitMs, onSelectIdleLimitMs) { sheet = null }
        "grace" -> OptionSheet("확인 대기 시간", AutoStopPolicy.GRACE_OPTIONS_MS,
            AutoStopPolicy::formatGraceLabel, graceMs, onSelectGraceMs) { sheet = null }
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
                    if (isSel) Icon(Icons.Filled.Check, contentDescription = "선택됨",
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
