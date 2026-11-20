package kr.co.nuni.gildam.ui.screens

import kr.co.nuni.gildam.ui.l10n.label
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kr.co.nuni.gildam.data.model.GaugeCell
import kr.co.nuni.gildam.data.model.GaugeLayout
import kr.co.nuni.gildam.data.model.GaugeMetric
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.ui.R
import kr.co.nuni.gildam.ui.illustration.TransportSticker
import kr.co.nuni.gildam.ui.l10n.labelWithUnitText
import kotlin.math.roundToInt

/** 계기판 칸 이름 */
internal val GaugeCell.label: String
    @Composable get() = when (this) {
        GaugeCell.ELEVATION -> stringResource(R.string.gauge_elevation_gain_loss)
        GaugeCell.WEATHER -> stringResource(R.string.gauge_weather)
        else -> metric.labelWithUnitText
    }

private val ROW_HEIGHT = 56.dp

/**
 * 설정 > 계기판 구성 (활동 유형별).
 * 활동 유형를 고르고, 칸을 켜고 끄고 순서를 바꾸며, 하나를 지도 위 강조 지표로 지정할 수 있습니다.
 * 지도 아래 계기판은 강조 지표를 빼 향목 중 짧수(2/4/6)개만 표시됩니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GaugeLayoutScreen(
    selectedType: TransportType,
    onSelectType: (TransportType) -> Unit,
    layout: GaugeLayout,
    overlay: GaugeMetric,
    onChangeLayout: (GaugeLayout) -> Unit,
    onChangeOverlay: (GaugeMetric) -> Unit,
    onReset: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gauge_layout_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                stringResource(R.string.gauge_layout_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            ActivityPicker(selected = selectedType, onSelect = onSelectType)
            Spacer(Modifier.height(16.dp))
            ReorderableGaugeList(
                layout = layout,
                overlay = overlay,
                onChangeLayout = onChangeLayout,
                onChangeOverlay = onChangeOverlay
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.gauge_layout_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onReset,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.gauge_layout_reset))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 끌어서 순서 바꾸기 + 켜고 끄기 + 지도 위에 크게 띄울 항목 선택 */
@Composable
private fun ReorderableGaugeList(
    layout: GaugeLayout,
    overlay: GaugeMetric,
    onChangeLayout: (GaugeLayout) -> Unit,
    onChangeOverlay: (GaugeMetric) -> Unit
) {
    val rowPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }
    var working by remember(layout) { mutableStateOf(layout) }
    var dragging by remember { mutableStateOf<GaugeCell?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val current = rememberUpdatedState(working)
    val saved = rememberUpdatedState(layout)
    val saveLayout = rememberUpdatedState(onChangeLayout)
    val moveUp = stringResource(R.string.gauge_layout_move_up)
    val moveDown = stringResource(R.string.gauge_layout_move_down)

    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column {
            working.entries.forEachIndexed { index, entry ->
              key(entry.cell) {
                val cell = entry.cell
                val isDragging = dragging == cell
                val bottomCount = working.gridCells(overlay, true).size
                val wouldBreakEven = entry.enabled && bottomCount <= 2 && cell.metric != overlay
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ROW_HEIGHT)
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                        .then(if (isDragging) Modifier.shadow(6.dp, RoundedCornerShape(12.dp)) else Modifier)
                        .background(if (isDragging) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant)
                        .semantics {
                            customActions = listOfNotNull(
                                if (index > 0) CustomAccessibilityAction(moveUp) { onChangeLayout(working.moved(index, index - 1)); true } else null,
                                if (index < working.entries.lastIndex) CustomAccessibilityAction(moveDown) { onChangeLayout(working.moved(index, index + 1)); true } else null
                            )
                        }
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(22.dp)
                    )
                    Text(
                        cell.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (entry.enabled) FontWeight.Medium else FontWeight.Normal,
                        color = if (entry.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // 지도 위에 크게 띄울 항목 (하나만, 다시 누르면 해제)
                    val isOverlay = cell.metric == overlay
                    OnMapToggle(
                        selected = isOverlay,
                        itemLabel = cell.label,
                        onClick = { onChangeOverlay(if (isOverlay) GaugeMetric.NONE else cell.metric) }
                    )
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = entry.enabled,
                        enabled = !wouldBreakEven,
                        onCheckedChange = { on -> onChangeLayout(working.toggled(cell, on)) }
                    )
                    // Drag handle
                    Box(
                        Modifier
                            .size(44.dp)
                            .pointerInput(cell) {
                                detectVerticalDragGestures(
                                    onDragStart = { dragging = cell; dragOffset = 0f },
                                    onVerticalDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount
                                        val list = current.value
                                        val from = list.entries.indexOfFirst { it.cell == cell }
                                        val steps = (dragOffset / rowPx).roundToInt()
                                        val to = (from + steps).coerceIn(0, list.entries.lastIndex)
                                        if (to != from) {
                                            working = list.moved(from, to)
                                            dragOffset -= (to - from) * rowPx
                                        }
                                    },
                                    onDragEnd = {
                                        dragging = null; dragOffset = 0f
                                        if (current.value != saved.value) saveLayout.value(current.value)
                                    },
                                    onDragCancel = {
                                        dragging = null; dragOffset = 0f
                                        if (current.value != saved.value) saveLayout.value(current.value)
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.DragHandle, contentDescription = stringResource(R.string.gauge_layout_drag_cd),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
              }
            }
        }
    }
}

/**
 * '지도 위' 알약 버튼. 고른 항목은 색이 채워지고, 나머지는 옅은 테두리만 보입니다.
 * 별표보다 '지도 위에 크게 뜬다'는 뜻이 바로 읽히도록 글자로 표시합니다.
 */
@Composable
private fun OnMapToggle(selected: Boolean, itemLabel: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val cd = stringResource(R.string.gauge_layout_on_map_cd, itemLabel)
    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) cs.primary else Color.Transparent,
        contentColor = if (selected) cs.onPrimary else cs.onSurfaceVariant.copy(alpha = 0.7f),
        border = if (selected) null else BorderStroke(1.dp, cs.outline.copy(alpha = 0.45f)),
        modifier = Modifier
            .heightIn(min = 32.dp)
            .semantics { contentDescription = cd }
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
            }
            Text(
                stringResource(R.string.gauge_layout_on_map),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityPicker(selected: TransportType, onSelect: (TransportType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val selectedLabel = selected.label
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedLabel,
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
                val tLabel = t.label
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TransportSticker(t, selected = t == selected, size = 24.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(tLabel, fontWeight = if (t == selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    },
                    onClick = { onSelect(t); expanded = false }
                )
            }
        }
    }
}
