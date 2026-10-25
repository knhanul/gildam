package kr.co.nuni.gildam.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.co.nuni.gildam.ui.theme.DarkModePref
import kr.co.nuni.gildam.ui.theme.GildamPalette
import kr.co.nuni.gildam.ui.theme.gildamColorScheme

/** 설정 > 테마 색: 팔레트 미리보기 카드 3개 + 밝기 모드 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSection(
    palette: GildamPalette,
    darkMode: DarkModePref,
    onSelectPalette: (GildamPalette) -> Unit,
    onSelectDarkMode: (DarkModePref) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("테마 색", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GildamPalette.selectable.forEachIndexed { i, p ->
                PaletteCard(p, ('A' + i).toString(), selected = p == palette, onClick = { onSelectPalette(p) }, modifier = Modifier.weight(1f))
            }
        }
        Text(palette.moodKo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text("화면 밝기", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            DarkModePref.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = m == darkMode, onClick = { onSelectDarkMode(m) },
                    shape = SegmentedButtonDefaults.itemShape(i, DarkModePref.entries.size)
                ) { Text(m.labelKo) }
            }
        }
    }
}

@Composable
private fun PaletteCard(p: GildamPalette, letter: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val cs = gildamColorScheme(p, dark = false)
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(cs.background)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) cs.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
    ) {
        // 미리보기: 하늘 그라데이션 + 언덕 + 해
        Box(Modifier.fillMaxWidth().height(70.dp)) {
            Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                val w = size.width; val h = size.height
                drawRect(Brush.verticalGradient(listOf(cs.primaryContainer, cs.background)))
                drawCircle(cs.tertiary, h * 0.17f, Offset(w * 0.78f, h * 0.3f))
                drawPath(Path().apply { moveTo(0f, h); lineTo(0f, h * 0.7f); cubicTo(w * 0.3f, h * 0.4f, w * 0.6f, h * 0.9f, w, h * 0.6f); lineTo(w, h); close() },
                    cs.secondary.copy(alpha = 0.6f))
                drawPath(Path().apply { moveTo(0f, h); cubicTo(w * 0.25f, h * 0.5f, w * 0.45f, h * 0.45f, w * 0.62f, h * 0.68f); cubicTo(w * 0.75f, h * 0.85f, w * 0.9f, h * 0.8f, w, h * 0.85f); lineTo(w, h); close() },
                    Brush.verticalGradient(listOf(cs.primary, lerp(cs.primary, cs.tertiary, 0.45f)), h * 0.45f, h))
            }
            if (selected) Box(
                Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp).clip(CircleShape).background(cs.primary),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Check, contentDescription = "선택됨", tint = cs.onPrimary, modifier = Modifier.size(15.dp)) }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(cs.primary, cs.secondary, cs.tertiary).forEach { Box(Modifier.size(14.dp).clip(CircleShape).background(it)) }
            }
            Spacer(Modifier.height(6.dp))
            Text("$letter · ${p.labelKo}", style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = cs.onSurface, maxLines = 1)
        }
    }
}

/** 설정 > 기록 중 지도 보기: 그림 카드 2개 */
@Composable
fun RecordMapModeSection(mode: kr.co.nuni.gildam.map.MapCameraMode, onSelect: (kr.co.nuni.gildam.map.MapCameraMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("기록 중 지도 보기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            kr.co.nuni.gildam.map.MapCameraMode.entries.forEach { m ->
                MapModeCard(m, m == mode, { onSelect(m) }, Modifier.weight(1f))
            }
        }
        Text("기록 화면 지도 위 버튼으로 언제든 바꿀 수 있어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MapModeCard(m: kr.co.nuni.gildam.map.MapCameraMode, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(if (selected) cs.primaryContainer else cs.surfaceVariant)
            .border(if (selected) 2.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant, shape)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(12.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp)).background(cs.surface)) {
            Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                val w = size.width; val h = size.height
                val route = Path().apply { moveTo(w * 0.1f, h * 0.8f); cubicTo(w * 0.35f, h * 0.95f, w * 0.3f, h * 0.25f, w * 0.6f, h * 0.4f); quadraticTo(w * 0.8f, h * 0.5f, w * 0.88f, h * 0.2f) }
                if (m == kr.co.nuni.gildam.map.MapCameraMode.OVERVIEW) {
                    drawPath(route, cs.primary, style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    drawCircle(cs.tertiary, 4.dp.toPx(), Offset(w * 0.1f, h * 0.8f))
                    drawCircle(cs.primary, 5.dp.toPx(), Offset(w * 0.88f, h * 0.2f))
                    val d = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                    drawRoundRect(cs.onSurfaceVariant.copy(alpha = 0.6f), Offset(w * 0.04f, h * 0.08f), androidx.compose.ui.geometry.Size(w * 0.92f, h * 0.84f),
                        androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx(), pathEffect = d))
                } else {
                    // 크게 확대된 길 + 가운데 내 위치
                    val c = Offset(w / 2, h / 2)
                    drawLine(cs.primary, Offset(-w * 0.1f, h * 1.1f), c, 7.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
                    drawCircle(cs.primary.copy(alpha = 0.22f), 16.dp.toPx(), c)
                    drawCircle(androidx.compose.ui.graphics.Color.White, 8.dp.toPx(), c)
                    drawCircle(cs.primary, 6.dp.toPx(), c)
                    drawLine(cs.tertiary, Offset(c.x, c.y - 22.dp.toPx()), Offset(c.x, c.y - 30.dp.toPx()), 2.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
            if (selected) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, "선택됨", tint = cs.onPrimary, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(m.labelKo, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) cs.onPrimaryContainer else cs.onSurface)
        Text(if (m == kr.co.nuni.gildam.map.MapCameraMode.FOLLOW) "내 위치를 가운데에, 빨라지면 넓게" else "출발부터 지금까지 한 화면에",
            style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
    }
}
