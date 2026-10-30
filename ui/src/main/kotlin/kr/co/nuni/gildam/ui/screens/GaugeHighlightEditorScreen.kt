package kr.co.nuni.gildam.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.nuni.gildam.data.model.GaugeMetric
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.ui.illustration.TransportSticker
import kr.co.nuni.gildam.ui.illustration.brandGradient
import kr.co.nuni.gildam.ui.illustration.onBrand
import kr.co.nuni.gildam.ui.theme.TabularStatHeroStyle

/**
 * 설정 > 기록 설정 > 지도 강조 지표 편집 화면.
 * 편집 중 변경은 미리보기에 즉시 반영하고, '완료'에서 바뀐 활동만 저장합니다.
 * 뒤로가기는 기존 저장값을 그대로 둡니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GaugeHighlightEditorScreen(
    savedSelections: Map<TransportType, GaugeMetric>,
    onSave: (Map<TransportType, GaugeMetric>) -> Unit,
    onNavigateBack: () -> Unit
) {
    val draft = remember {
        mutableStateMapOf<TransportType, GaugeMetric>().apply {
            TransportType.entries.forEach { put(it, savedSelections[it] ?: GaugeMetric.defaultFor(it)) }
        }
    }
    var selectedType by rememberSaveable { mutableStateOf(TransportType.entries.first().name) }
    val type = TransportType.valueOf(selectedType)
    val metric = draft[type] ?: GaugeMetric.defaultFor(type)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("지도 강조 지표") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    TextButton(onClick = { onSave(draft.toMap()); onNavigateBack() }) {
                        Text("완료", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(2.dp))

            // 상단: 편집할 활동 선택 (다른 활동이 있다는 것을 드롭다운으로 명시)
            ActivityPicker(
                selected = type,
                onSelect = { selectedType = it.name }
            )

            // 중앙: 지도 위 표시 미리보기 (예시 지도·예시 값, 실제 위치·기록 사용 안 함)
            Text("미리보기", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            PreviewMap(metric = metric)
            Text(
                "예시 지도와 예시 값이에요. 실제 위치나 기록을 사용하지 않아요.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 하단: 선택 가능한 지표 목록 ('표시 안 함' 포함)
            Text("지도에 크게 표시할 정보", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            ) {
                Column {
                    GaugeMetric.selectable.forEach { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.RadioButton) { draft[type] = m }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = metric == m, onClick = null)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                m.labelKo,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (metric == m) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // 설명 + 현재 활동만 기본값으로 되돌리기
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "지도에서 크게 볼 값을 골라 주세요.\n계기판의 항목과 위치는 바뀌지 않아요.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { draft[type] = GaugeMetric.defaultFor(type) }) {
                    Text("이 활동 기본값으로", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityPicker(selected: TransportType, onSelect: (TransportType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected.labelKo,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            leadingIcon = { TransportSticker(selected, selected = true, size = 26.dp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = cs.surfaceVariant.copy(alpha = 0.55f),
                unfocusedContainerColor = cs.surfaceVariant.copy(alpha = 0.55f),
                focusedBorderColor = cs.primary,
                unfocusedBorderColor = cs.outlineVariant
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = cs.surface,
            shape = RoundedCornerShape(12.dp)
        ) {
            TransportType.entries.forEach { t ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TransportSticker(t, selected = t == selected, size = 24.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(t.labelKo, fontWeight = if (t == selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    },
                    onClick = { onSelect(t); expanded = false }
                )
            }
        }
    }
}

/** 예시 지도: 공원·도로·강·경로만 그리는 단순 일러스트 (실제 지도·위치 아님) */
@Composable
private fun PreviewMap(metric: GaugeMetric) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            // 바탕(밝은 지도색)과 공원·강 느낌의 블록
            drawRect(cs.surfaceVariant.copy(alpha = 0.8f))
            drawRect(cs.primary.copy(alpha = 0.10f), size = androidx.compose.ui.geometry.Size(w * 0.38f, h * 0.5f))
            drawRect(cs.tertiary.copy(alpha = 0.18f), topLeft = Offset(w * 0.82f, 0f), size = androidx.compose.ui.geometry.Size(w * 0.18f, h))
            // 도로
            val road = Path().apply { moveTo(0f, h * 0.72f); cubicTo(w * 0.3f, h * 0.6f, w * 0.6f, h * 0.82f, w, h * 0.68f) }
            drawPath(road, cs.outlineVariant, style = Stroke(width = h * 0.07f))
            val road2 = Path().apply { moveTo(w * 0.15f, 0f); lineTo(w * 0.45f, h) }
            drawPath(road2, cs.outlineVariant.copy(alpha = 0.7f), style = Stroke(width = h * 0.05f))
            // 예시 경로 (주황 폴리라인 + 출발·도착 점)
            val route = Path().apply {
                moveTo(w * 0.08f, h * 0.8f)
                cubicTo(w * 0.3f, h * 0.55f, w * 0.42f, h * 0.75f, w * 0.55f, h * 0.55f)
                cubicTo(w * 0.66f, h * 0.38f, w * 0.7f, h * 0.35f, w * 0.82f, h * 0.3f)
            }
            drawPath(route, cs.secondary, style = Stroke(width = 7.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            drawCircle(cs.secondary, radius = 8.dp.toPx(), center = Offset(w * 0.08f, h * 0.8f))
            drawCircle(cs.surface, radius = 4.dp.toPx(), center = Offset(w * 0.08f, h * 0.8f))
            drawCircle(cs.secondary, radius = 10.dp.toPx(), center = Offset(w * 0.82f, h * 0.3f))
            drawCircle(cs.surface, radius = 4.5f.dp.toPx(), center = Offset(w * 0.82f, h * 0.3f))
        }
        if (metric == GaugeMetric.NONE) {
            Text(
                "지도 위에 크게 띄우는 값이 없어요",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            PreviewChip(metric = metric, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** 지도 위 칩 미리보기 — 실제 GaugeOverlayChip 과 같은 모양, 값은 예시 */
@Composable
private fun PreviewChip(metric: GaugeMetric, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val (value, unit) = when (metric) {
        GaugeMetric.DISTANCE -> "5.20" to "km"
        GaugeMetric.CURRENT_SPEED -> "24.6" to "km/h"
        GaugeMetric.MOVING_AVG_SPEED -> "18.3" to "km/h"
        GaugeMetric.TOTAL_TIME -> "01:12:45" to null
        GaugeMetric.CLOCK -> "15:28" to null
        GaugeMetric.NONE -> "" to null
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = cs.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.5.dp, cs.primary.copy(alpha = 0.55f)),
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 20.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(brandGradient()),
                contentAlignment = Alignment.Center
            ) {
                Icon(metric.icon, contentDescription = null, tint = onBrand(), modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = value,
                style = TabularStatHeroStyle.copy(fontSize = 32.sp, lineHeight = 36.sp),
                color = cs.onSurface
            )
            unit?.let {
                Spacer(Modifier.width(5.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Bottom).padding(bottom = 4.dp)
                )
            }
        }
    }
}
