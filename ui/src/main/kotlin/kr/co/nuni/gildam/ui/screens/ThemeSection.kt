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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.sp
import kr.co.nuni.gildam.ui.theme.DarkModePref
import kr.co.nuni.gildam.ui.theme.GildamPalette
import kr.co.nuni.gildam.ui.theme.gildamColorScheme

/** 설정 > 테마 색 시트: 팔레트 미리보기 카드 3개 (기존 '햇살 산책/민트 소풍/노을 러닝' 일러스트 재사용) */
@Composable
internal fun ThemePickerSheetContent(
    palette: GildamPalette,
    onSelectPalette: (GildamPalette) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GildamPalette.selectable.forEachIndexed { i, p ->
                PaletteCard(p, ('A' + i).toString(), selected = p == palette, onClick = { onSelectPalette(p) }, modifier = Modifier.weight(1f))
            }
        }
        Text(
            palette.moodKo,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 설정 > 화면 모드 시트: 시스템/밝게/어둡게 선택 행 (저장값·시스템 연동은 기존 그대로) */
@Composable
internal fun DarkModeSheetContent(
    darkMode: DarkModePref,
    onSelectDarkMode: (DarkModePref) -> Unit
) {
    Column {
        DarkModePref.entries.forEach { mode ->
            val selected = mode == darkMode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.RadioButton) { onSelectDarkMode(mode) }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.RadioButton(selected = selected, onClick = null)
                Spacer(Modifier.width(4.dp))
                Text(
                    mode.labelKo,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun PaletteCard(p: GildamPalette, letter: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val cs = gildamColorScheme(p, dark = false)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.clip(shape).background(cs.background)
            .border(if (selected) 2.dp else 1.dp, if (selected) cs.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
    ) {
        // 미리보기: 하늘 그라데이션 + 언덕 + 해
        Box(Modifier.fillMaxWidth().height(44.dp)) {
            Canvas(Modifier.fillMaxWidth().height(44.dp)) {
                val w = size.width; val h = size.height
                drawRect(Brush.verticalGradient(listOf(cs.primaryContainer, cs.background)))
                drawCircle(cs.tertiary, h * 0.17f, Offset(w * 0.78f, h * 0.3f))
                drawPath(Path().apply { moveTo(0f, h); lineTo(0f, h * 0.7f); cubicTo(w * 0.3f, h * 0.4f, w * 0.6f, h * 0.9f, w, h * 0.6f); lineTo(w, h); close() },
                    cs.secondary.copy(alpha = 0.6f))
                drawPath(Path().apply { moveTo(0f, h); cubicTo(w * 0.25f, h * 0.5f, w * 0.45f, h * 0.45f, w * 0.62f, h * 0.68f); cubicTo(w * 0.75f, h * 0.85f, w * 0.9f, h * 0.8f, w, h * 0.85f); lineTo(w, h); close() },
                    Brush.verticalGradient(listOf(cs.primary, lerp(cs.primary, cs.tertiary, 0.45f)), h * 0.45f, h))
            }
            if (selected) Box(
                Modifier.align(Alignment.TopStart).padding(4.dp).size(18.dp).clip(CircleShape).background(cs.primary),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Check, contentDescription = "선택됨", tint = cs.onPrimary, modifier = Modifier.size(12.dp)) }
        }
        Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(cs.primary, cs.secondary, cs.tertiary).forEach { Box(Modifier.size(10.dp).clip(CircleShape).background(it)) }
            }
            Spacer(Modifier.height(3.dp))
            Text("$letter · ${p.labelKo}", style = MaterialTheme.typography.labelMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = cs.onSurface, maxLines = 1)
        }
    }
}
