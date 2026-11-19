package kr.co.nuni.gildam.ui.screens

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kr.co.nuni.gildam.data.model.GaugeCell
import kr.co.nuni.gildam.data.model.GaugeLayout
import kr.co.nuni.gildam.ui.R
import kr.co.nuni.gildam.ui.l10n.labelWithUnitText
import kotlin.math.roundToInt

/** 계기판 칸 이름 */
internal val GaugeCell.label: String
    @Composable get() = when (this) {
        GaugeCell.ELEVATION -> stringResource(R.string.gauge_elevation_gain_loss)
        GaugeCell.WEATHER -> stringResource(R.string.gauge_weather)
        else -> metric!!.labelWithUnitText
    }

private val ROW_HEIGHT = 56.dp

/**
 * 설정 > 계기판 구성.
 * 칸마다 켜고 끄고, 오른쪽 손잡이를 끌어 순서를 바꿉니다. 바꾸면 바로 저장됩니다.
 * 계기판은 켠 칸을 이 순서대로 2열로 놓고, 홀수면 마지막 칸을 넓게 씁니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GaugeLayoutScreen(
    layout: GaugeLayout,
    onChange: (GaugeLayout) -> Unit,
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
            GaugeLayoutPreview(layout)
            Spacer(Modifier.height(16.dp))
            ReorderableGaugeList(layout, onChange)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.gauge_layout_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onReset,
                enabled = layout != GaugeLayout.DEFAULT,
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

/** 기록 중 계기판 모양 미리보기 (이름만, 2열·홀수면 마지막 칸 넓게) */
@Composable
private fun GaugeLayoutPreview(layout: GaugeLayout) {
    val cells = layout.enabledCells
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.gauge_layout_preview), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            cells.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { cell ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(cell.label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

/** 끌어서 순서 바꾸기 + 켜고 끄기 목록 */
@Composable
private fun ReorderableGaugeList(layout: GaugeLayout, onChange: (GaugeLayout) -> Unit) {
    val rowPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }
    // 끄는 동안은 화면 안에서만 순서를 바꾸고, 손을 떼면 저장
    var working by remember(layout) { mutableStateOf(layout) }
    var dragging by remember { mutableStateOf<GaugeCell?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val current = rememberUpdatedState(working)
    val saved = rememberUpdatedState(layout)
    val save = rememberUpdatedState(onChange)
    val moveUp = stringResource(R.string.gauge_layout_move_up)
    val moveDown = stringResource(R.string.gauge_layout_move_down)
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column {
            working.entries.forEachIndexed { index, entry ->
              // 칸마다 key 를 둬서 순서가 바뀌어도 끌던 손잡이가 끊기지 않게
              key(entry.cell) {
                val cell = entry.cell
                val isDragging = dragging == cell
                val onlyOne = entry.enabled && working.enabledCells.size == 1
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
                                if (index > 0) CustomAccessibilityAction(moveUp) { onChange(working.moved(index, index - 1)); true } else null,
                                if (index < working.entries.lastIndex) CustomAccessibilityAction(moveDown) { onChange(working.moved(index, index + 1)); true } else null
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
                    Switch(
                        checked = entry.enabled,
                        enabled = !onlyOne,
                        onCheckedChange = { on -> onChange(working.toggled(cell, on)) }
                    )
                    // 끌기 손잡이: 위아래로 끌어 순서 바꾸기
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
                                        if (current.value != saved.value) save.value(current.value)
                                    },
                                    onDragCancel = {
                                        dragging = null; dragOffset = 0f
                                        if (current.value != saved.value) save.value(current.value)
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
