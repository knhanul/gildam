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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.RestartAlt
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kr.co.nuni.gildam.data.model.GaugeMetric
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.ui.illustration.TransportSticker

/**
 * 테스트/미리보기에서 특정 활동의 드롭다운을 펼친 채로 그릴 때 사용 (활동 이름 일치).
 * 실제 앱에서는 항상 null 이라 아무 영향이 없습니다.
 */
val LocalGaugeDropdownPreview = androidx.compose.runtime.compositionLocalOf<TransportType?> { null }

/** 설정 > 지도 위 계기판: 활동 한 줄에 드롭다운 하나 (간단하게) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GaugeSection(
    selections: Map<TransportType, GaugeMetric>,
    onSelect: (TransportType, GaugeMetric) -> Unit,
    /** 활동 5개를 모두 기본값으로 되돌림. null 이면 버튼을 숨김 */
    onResetAll: (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "지도 위 계기판",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (onResetAll != null) {
                TextButton(
                    onClick = onResetAll,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.width(14.dp).height(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("기본값으로", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                }
            }
        }
        Text(
            "활동마다 지도에 크게 띄울 값 하나를 고르세요. 나머지 네 개는 지도 아래에 보여요.",
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
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                TransportType.entries.forEach { type ->
                    GaugeRow(
                        type = type,
                        current = selections[type] ?: GaugeMetric.defaultFor(type),
                        onSelect = { metric -> onSelect(type, metric) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GaugeRow(
    type: TransportType,
    current: GaugeMetric,
    onSelect: (GaugeMetric) -> Unit
) {
    val previewExpanded = LocalGaugeDropdownPreview.current == type
    var expanded by remember(previewExpanded) { mutableStateOf(previewExpanded) }
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TransportSticker(type, selected = true, size = 24.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = type.labelKo,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = cs.onSurface,
            modifier = Modifier.width(46.dp)
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = current.labelKo,
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
                    .semantics { contentDescription = "${type.labelKo} 지도 위 계기판" }
            )
            // 팝업 배경도 테마 표면색으로 맞춤 (기본값은 M3 기준 보라색 계열이 나옴)
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = cs.surface,
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 6.dp
            ) {
                GaugeMetric.selectable.forEach { metric ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                metric.labelKo,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (metric == current) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSelect(metric)
                            expanded = false
                        },
                        trailingIcon = if (metric == current) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.width(16.dp).height(16.dp)) }
                        } else null
                    )
                }
            }
        }
    }
}
