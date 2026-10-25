package kr.co.nuni.gildam.ui.illustration

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.co.nuni.gildam.data.model.TransportType

/*
 * 길담 일러스트 (직접 그린 벡터, 외부 이미지 없음).
 * 모든 그림은 24단위 격자 기준으로 그리고, 색은 현재 테마(MaterialTheme.colorScheme)에서 가져와
 * 팔레트를 바꾸면 그림 색도 함께 바뀝니다.
 */

private class U(val s: Float) { fun o(x: Float, y: Float) = Offset(x * s, y * s) }

private fun DrawScope.line(u: U, c: Color, w: Float, vararg pts: Pair<Float, Float>) {
    val p = Path().apply { pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * u.s, y * u.s) else lineTo(x * u.s, y * u.s) } }
    drawPath(p, c, style = Stroke(w * u.s, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** 이동 수단 그림 (선 굵은 픽토그램) */
fun DrawScope.drawTransportGlyph(type: TransportType, c: Color) {
    val u = U(size.minDimension / 24f); val w = 2.4f
    when (type) {
        TransportType.WALK -> {
            drawCircle(c, 2.2f * u.s, u.o(13f, 4.6f))
            line(u, c, w, 12.4f to 8.4f, 11.2f to 14f)
            line(u, c, w, 9f to 11.8f, 12.2f to 9.4f, 15.2f to 12.2f)
            line(u, c, w, 7.8f to 20f, 11.2f to 14f, 14.6f to 20f)
        }
        TransportType.HIKE -> {
            drawCircle(c, 2.1f * u.s, u.o(12.6f, 4.4f))
            drawRoundRect(c, u.o(6.6f, 7.4f), Size(3.6f * u.s, 5.8f * u.s), CornerRadius(1.2f * u.s))
            line(u, c, w, 12f to 8.2f, 11f to 14f)
            line(u, c, w, 12f to 9.6f, 15.6f to 11.4f)
            line(u, c, w, 8f to 20f, 11f to 14f, 13.6f to 20f)
            line(u, c, 1.8f, 16.4f to 9.4f, 18f to 20.2f)
        }
        TransportType.RUN -> {
            drawCircle(c, 2.2f * u.s, u.o(15.4f, 4.2f))
            line(u, c, w, 14.2f to 8.2f, 11.2f to 13.4f)
            line(u, c, w, 9.4f to 8.2f, 13.6f to 9f, 17f to 11.4f)
            line(u, c, w, 11.2f to 13.4f, 15f to 16f, 14f to 20.4f)
            line(u, c, w, 11.2f to 13.4f, 8.6f to 16.6f, 5f to 16.2f)
            line(u, c, 1.4f, 2.6f to 10f, 6f to 10f); line(u, c, 1.4f, 3.6f to 13f, 6.4f to 13f)
        }
        TransportType.BIKE -> {
            drawCircle(c, 4f * u.s, u.o(6f, 16f), style = Stroke(2f * u.s))
            drawCircle(c, 4f * u.s, u.o(18f, 16f), style = Stroke(2f * u.s))
            line(u, c, 2f, 6f to 16f, 10f to 9.6f, 15.4f to 9.6f, 18f to 16f)
            line(u, c, 2f, 10f to 9.6f, 12f to 16f, 15.4f to 9.6f)
            line(u, c, 2f, 8.4f to 7.6f, 11f to 7.6f); line(u, c, 2f, 14.6f to 7f, 16.6f to 7f)
        }
        TransportType.CAR -> {
            val roof = Path().apply {
                moveTo(5.6f * u.s, 10.4f * u.s); lineTo(8.2f * u.s, 5.8f * u.s); lineTo(15.8f * u.s, 5.8f * u.s); lineTo(18.4f * u.s, 10.4f * u.s); close()
            }
            drawPath(roof, c)
            drawRoundRect(c, u.o(2.6f, 9.8f), Size(18.8f * u.s, 7f * u.s), CornerRadius(2.6f * u.s))
            drawCircle(Color.White.copy(alpha = 0.9f), 1.1f * u.s, u.o(5.6f, 12.8f)); drawCircle(Color.White.copy(alpha = 0.9f), 1.1f * u.s, u.o(18.4f, 12.8f))
            drawCircle(c, 2.4f * u.s, u.o(7.4f, 17.4f)); drawCircle(c, 2.4f * u.s, u.o(16.6f, 17.4f))
        }
    }
}

@Composable
fun brandGradient(): Brush {
    val cs = MaterialTheme.colorScheme
    return Brush.linearGradient(listOf(cs.primary, lerp(cs.primary, cs.tertiary, 0.55f)))
}

/** 그라데이션 위 글자색: 밝은 테마는 흰색, 어두운 테마는 진한 onPrimary (밝은 그라데이션 위 대비 확보) */
@Composable
fun onBrand(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) MaterialTheme.colorScheme.onPrimary else Color.White

/** 동그란 스티커 아이콘 (선택 시 그라데이션 + 흰 그림) */
@Composable
fun TransportSticker(type: TransportType, selected: Boolean, size: Dp = 30.dp) {
    val cs = MaterialTheme.colorScheme
    val fg = onBrand()
    val bg = if (selected) brandGradient() else Brush.linearGradient(listOf(cs.surface, cs.surface))
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size * 0.72f)) { drawTransportGlyph(type, if (selected) fg else cs.primary) }
    }
}

/** 이동 수단 칩: 스티커 + 이름 */
@Composable
fun TransportChip(type: TransportType, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) cs.primaryContainer else cs.surfaceVariant)
            .then(if (selected) Modifier.border(1.5.dp, cs.primary, RoundedCornerShape(50)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 5.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TransportSticker(type, selected)
        Spacer(Modifier.width(7.dp))
        Text(type.labelKo, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant)
    }
}

/** 크고 생기있는 행동 버튼 (그라데이션 + 일러스트 배지 + 장식 원) */
@Composable
fun VividActionButton(title: String, subtitle: String?, type: TransportType, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val fg = onBrand()
    Box(
        modifier.fillMaxWidth().height(68.dp)
            .shadow(10.dp, RoundedCornerShape(24.dp), ambientColor = cs.primary, spotColor = cs.primary)
            .clip(RoundedCornerShape(24.dp)).background(brandGradient()).clickable(onClick = onClick)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = 0.14f), size.height * 0.9f, Offset(size.width * 0.92f, size.height * 0.1f))
            drawCircle(Color.White.copy(alpha = 0.10f), size.height * 0.45f, Offset(size.width * 0.74f, size.height * 1.05f))
            listOf(0.62f to 0.28f, 0.83f to 0.62f, 0.7f to 0.72f).forEach { (x, y) ->
                drawCircle(Color.White.copy(alpha = 0.55f), 2.2.dp.toPx(), Offset(size.width * x, size.height * y))
            }
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(if (fg == Color.White) Color.White else cs.surface), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(30.dp)) { drawTransportGlyph(type, cs.primary) }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = fg)
                subtitle?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.85f)) }
            }
        }
    }
}

enum class TabIllo { RECORD, LIST, COURSES, SETTINGS }

/** 하단 탭 아이콘: 선택 시 두 가지 색 채움, 아니면 선만 */
@Composable
fun TabIllustration(kind: TabIllo, selected: Boolean, size: Dp = 26.dp) {
    val cs = MaterialTheme.colorScheme
    val main = if (selected) cs.primary else cs.onSurfaceVariant
    val accent = if (selected) cs.tertiary else cs.onSurfaceVariant
    val accent2 = if (selected) cs.secondary else cs.onSurfaceVariant
    Canvas(Modifier.size(size)) {
        val u = U(this.size.minDimension / 24f)
        when (kind) {
            TabIllo.RECORD -> {
                drawCircle(main, 9.4f * u.s, u.o(12f, 12f), style = Stroke(2.2f * u.s))
                if (selected) drawCircle(Brush.linearGradient(listOf(cs.primary, cs.tertiary), u.o(7f, 7f), u.o(17f, 17f)), 5.2f * u.s, u.o(12f, 12f))
                else drawCircle(main, 4.6f * u.s, u.o(12f, 12f), style = Stroke(2f * u.s))
            }
            TabIllo.LIST -> {
                listOf(4.5f, 10.5f, 16.5f).forEachIndexed { i, y ->
                    val c = if (i == 0) main else main.copy(alpha = if (selected) 0.55f else 1f)
                    if (selected) drawRoundRect(c, u.o(8.5f, y), Size(12.5f * u.s, 3.4f * u.s), CornerRadius(1.7f * u.s))
                    else drawRoundRect(c, u.o(8.5f, y), Size(12.5f * u.s, 3.4f * u.s), CornerRadius(1.7f * u.s), style = Stroke(1.8f * u.s))
                    drawCircle(if (i == 0) accent else accent2, 1.9f * u.s, u.o(4.6f, y + 1.7f))
                }
            }
            TabIllo.COURSES -> {
                val m = Path().apply {
                    moveTo(1.8f * u.s, 20f * u.s); lineTo(8.6f * u.s, 8.4f * u.s); lineTo(12.6f * u.s, 14.4f * u.s)
                    lineTo(15.6f * u.s, 10.6f * u.s); lineTo(22.2f * u.s, 20f * u.s); close()
                }
                if (selected) drawPath(m, Brush.verticalGradient(listOf(cs.primary, lerp(cs.primary, cs.secondary, 0.6f)), 8f * u.s, 20f * u.s))
                else drawPath(m, main, style = Stroke(2f * u.s, join = StrokeJoin.Round))
                if (selected) {
                    val snow = Path().apply { moveTo(6.9f * u.s, 11.3f * u.s); lineTo(8.6f * u.s, 8.4f * u.s); lineTo(10.4f * u.s, 11.1f * u.s); lineTo(8.6f * u.s, 10.2f * u.s); close() }
                    drawPath(snow, Color.White)
                }
                drawCircle(accent, 2.6f * u.s, u.o(18.6f, 5.4f), style = if (selected) Fill else Stroke(1.8f * u.s))
            }
            TabIllo.SETTINGS -> {
                for (k in 0 until 8) {
                    val a = Math.toRadians(k * 45.0)
                    val ix = 12f + 6.6f * kotlin.math.cos(a).toFloat(); val iy = 12f + 6.6f * kotlin.math.sin(a).toFloat()
                    val ox = 12f + 9.6f * kotlin.math.cos(a).toFloat(); val oy = 12f + 9.6f * kotlin.math.sin(a).toFloat()
                    line(u, main, 3f, ix to iy, ox to oy)
                }
                if (selected) drawCircle(main, 7.2f * u.s, u.o(12f, 12f)) else drawCircle(main, 6.8f * u.s, u.o(12f, 12f), style = Stroke(2f * u.s))
                drawCircle(if (selected) cs.tertiary else main, 2.8f * u.s, u.o(12f, 12f), style = if (selected) Fill else Stroke(1.8f * u.s))
            }
        }
    }
}

/** 빈 코스 일러스트: 햇살 아래 언덕과 정상 깃발까지 이어진 길 */
@Composable
fun EmptyCoursesIllustration(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Canvas(modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(28.dp))) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(listOf(cs.primaryContainer, cs.surface)))
        drawCircle(cs.tertiary.copy(alpha = 0.25f), h * 0.32f, Offset(w * 0.8f, h * 0.28f))
        drawCircle(cs.tertiary, h * 0.17f, Offset(w * 0.8f, h * 0.28f))
        val back = Path().apply { moveTo(0f, h * 0.72f); cubicTo(w * 0.2f, h * 0.38f, w * 0.42f, h * 0.5f, w * 0.6f, h * 0.62f); cubicTo(w * 0.78f, h * 0.74f, w * 0.9f, h * 0.5f, w, h * 0.56f); lineTo(w, h); lineTo(0f, h); close() }
        drawPath(back, cs.secondary.copy(alpha = 0.45f))
        val front = Path().apply { moveTo(0f, h); lineTo(0f, h * 0.86f); cubicTo(w * 0.2f, h * 0.8f, w * 0.34f, h * 0.36f, w * 0.48f, h * 0.3f); cubicTo(w * 0.6f, h * 0.36f, w * 0.78f, h * 0.84f, w, h * 0.8f); lineTo(w, h); close() }
        drawPath(front, Brush.verticalGradient(listOf(cs.primary, lerp(cs.primary, cs.secondary, 0.5f)), h * 0.3f, h))
        val trail = Path().apply { moveTo(w * 0.3f, h); cubicTo(w * 0.5f, h * 0.84f, w * 0.34f, h * 0.62f, w * 0.46f, h * 0.48f); quadraticTo(w * 0.5f, h * 0.4f, w * 0.48f, h * 0.33f) }
        drawPath(trail, Color.White.copy(alpha = 0.9f), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))))
        val top = Offset(w * 0.48f, h * 0.3f)
        drawLine(cs.onSurface, top, top.copy(y = top.y - h * 0.26f), 2.5.dp.toPx(), StrokeCap.Round)
        val flag = Path().apply { moveTo(top.x, top.y - h * 0.26f); lineTo(top.x + w * 0.09f, top.y - h * 0.2f); lineTo(top.x, top.y - h * 0.14f); close() }
        drawPath(flag, cs.secondary)
        listOf(0.18f to 0.2f, 0.3f to 0.12f, 0.62f to 0.1f).forEach { (x, y) -> drawCircle(Color.White, 3.dp.toPx(), Offset(w * x, h * y)) }
    }
}

/** 빈 기록 일러스트: 접힌 지도 위 발자국과 도착 핀 */
@Composable
fun EmptyRecordsIllustration(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Canvas(modifier.fillMaxWidth().height(150.dp)) {
        val w = size.width; val h = size.height
        val mw = w * 0.7f; val left = (w - mw) / 2; val panel = mw / 3
        for (i in 0 until 3) {
            val skew = if (i % 2 == 0) 0f else h * 0.06f
            val p = Path().apply {
                moveTo(left + panel * i, h * 0.12f + (if (i % 2 == 0) h * 0.06f else 0f))
                lineTo(left + panel * (i + 1), h * 0.12f + skew)
                lineTo(left + panel * (i + 1), h * 0.88f + skew)
                lineTo(left + panel * i, h * 0.88f + (if (i % 2 == 0) h * 0.06f else 0f)); close()
            }
            drawPath(p, if (i == 1) cs.primaryContainer else cs.surfaceVariant)
        }
        val route = Path().apply { moveTo(left + mw * 0.08f, h * 0.78f); cubicTo(left + mw * 0.4f, h * 0.9f, left + mw * 0.3f, h * 0.35f, left + mw * 0.62f, h * 0.45f); quadraticTo(left + mw * 0.8f, h * 0.5f, left + mw * 0.82f, h * 0.36f) }
        drawPath(route, cs.primary, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 18f))))
        drawPath(route, cs.primary.copy(alpha = 0.25f), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
        val pin = Offset(left + mw * 0.82f, h * 0.3f)
        val r = h * 0.11f
        val drop = Path().apply { moveTo(pin.x, pin.y + r * 1.9f); cubicTo(pin.x - r * 1.5f, pin.y + r * 0.4f, pin.x - r, pin.y - r, pin.x, pin.y - r); cubicTo(pin.x + r, pin.y - r, pin.x + r * 1.5f, pin.y + r * 0.4f, pin.x, pin.y + r * 1.9f); close() }
        drawPath(drop, Brush.verticalGradient(listOf(cs.secondary, cs.tertiary), pin.y - r, pin.y + r * 2))
        drawCircle(Color.White, r * 0.42f, pin)
        drawCircle(cs.tertiary, h * 0.05f, Offset(left + mw * 0.08f, h * 0.78f))
    }
}
