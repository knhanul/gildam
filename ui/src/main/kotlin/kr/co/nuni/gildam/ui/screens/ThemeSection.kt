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
