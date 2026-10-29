package kr.co.nuni.gildam.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.nuni.gildam.recording.AutoStopPolicy

/**
 * 설정 > 기록 화면 및 자동 종료 섹션 (`GaugeSection` 스타일의 카드 + 스위치/콤보박스)
 * - 기록 중 화면 켜 두기 (기본 OFF)
 * - 최대 기록 시간 (2/4/6/8/10시간 + 사용 안 함, 기본 4시간)
 * - 오래 멈춤 기준 (10/20/30/40/50/60분 + 사용 안 함, 기본 20분)
 * - 확인 대기 시간 (5/10/15분, 기본 10분)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingSection(
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    maxDurationMs: Long?,
    onSelectMaxDurationMs: (Long?) -> Unit,
    idleLimitMs: Long?,
    onSelectIdleLimitMs: (Long?) -> Unit,
    graceMs: Long,
    onSelectGraceMs: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "기록 화면 및 자동 종료",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "화면을 계속 켜 두거나, 깜빡하고 끄지 않았을 때 알림으로 먼저 물어본 뒤 기록을 마쳐요.",
            style = MaterialTheme.typography.bodySmall,
            fontSize = 9.5.sp,
            lineHeight = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "기록 중 화면 켜 두기",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "기록 화면을 보고 있는 동안 화면이 꺼지지 않아요.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 9.5.sp,
                            lineHeight = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = keepScreenOn,
                        onCheckedChange = onKeepScreenOnChange,
                        modifier = Modifier.semantics { contentDescription = "기록 중 화면 켜 두기" }
                    )
                }

                RecordingOptionDropdownRow(
                    label = "최대 기록 시간",
                    selectedLabel = AutoStopPolicy.formatMaxDurationLabel(maxDurationMs),
                    options = AutoStopPolicy.MAX_DURATION_OPTIONS_MS,
                    optionLabel = AutoStopPolicy::formatMaxDurationLabel,
                    isSelected = { it == maxDurationMs },
                    onSelect = onSelectMaxDurationMs
                )

                RecordingOptionDropdownRow(
                    label = "오래 멈춤 기준",
                    selectedLabel = AutoStopPolicy.formatIdleLimitLabel(idleLimitMs),
                    options = AutoStopPolicy.IDLE_LIMIT_OPTIONS_MS,
                    optionLabel = AutoStopPolicy::formatIdleLimitLabel,
                    isSelected = { it == idleLimitMs },
                    onSelect = onSelectIdleLimitMs
                )

                RecordingOptionDropdownRow(
                    label = "확인 대기 시간",
                    selectedLabel = AutoStopPolicy.formatGraceLabel(graceMs),
                    options = AutoStopPolicy.GRACE_OPTIONS_MS,
                    optionLabel = AutoStopPolicy::formatGraceLabel,
                    isSelected = { it == graceMs },
                    onSelect = onSelectGraceMs
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> RecordingOptionDropdownRow(
    label: String,
    selectedLabel: String,
    options: List<T>,
    optionLabel: (T) -> String,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = cs.onSurface,
            modifier = Modifier.width(96.dp)
        )
        Spacer(Modifier.width(8.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                textStyle = MaterialTheme.typography.labelLarge,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = cs.surface,
                    unfocusedContainerColor = cs.surface,
                    focusedBorderColor = cs.primary,
                    unfocusedBorderColor = cs.outlineVariant
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .height(52.dp)
                    .semantics { contentDescription = label }
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = cs.surface,
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 6.dp
            ) {
                options.forEach { option ->
                    val currentSelected = isSelected(option)
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = optionLabel(option),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (currentSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                        trailingIcon = if (currentSelected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.width(16.dp).height(16.dp)) }
                        } else null
                    )
                }
            }
        }
    }
}
