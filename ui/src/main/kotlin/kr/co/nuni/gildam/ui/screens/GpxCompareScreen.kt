package kr.co.nuni.gildam.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.nuni.gildam.data.gpxcompare.GpxRuggedness
import kr.co.nuni.gildam.data.gpxcompare.RuggednessGrade
import kr.co.nuni.gildam.data.gpxcompare.RuggednessResult
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** 비교 항목이 GPX 파일에서 왔는지, 내 코스에서 왔는지 */
enum class CompareSource { FILE, COURSE }

sealed interface GpxCompareEntry {
    val id: String
    data class Loaded(
        override val id: String,
        val result: RuggednessResult,
        val source: CompareSource = CompareSource.FILE
    ) : GpxCompareEntry
    data class Failed(override val id: String, val name: String, val message: String) : GpxCompareEntry
}

/** 비교 선 색 (테마 톤과 어울리는 4색) */
/** 비교 선 색: 현재 테마의 주색·보조색·강조색 + 중간색 */
internal val CompareColors: List<Color>
    @Composable get() {
        val cs = MaterialTheme.colorScheme
        return listOf(cs.primary, cs.secondary, cs.tertiary, lerp(cs.primary, cs.onSurface, 0.45f))
    }
private val BucketColors = listOf(Color(0xFFD4E8DC), Color(0xFF8AB89E), Color(0xFFE9C46A), Color(0xFFE07A4F), Color(0xFFA63A22))

@Composable
internal fun gradeColor(g: RuggednessGrade): Pair<Color, Color> {
    // 쉬움(초록)→매우 험함(빨강) 의미 색을 유지하되 테마 표면색과 섞어 팔레트에 어울리게
    val cs = MaterialTheme.colorScheme
    val dark = cs.surface.luminance() < 0.5f
    val base = when (g) {
        RuggednessGrade.EASY -> Color(0xFF2FA866)
        RuggednessGrade.MODERATE -> Color(0xFF8DB23A)
        RuggednessGrade.SOMEWHAT_HARD -> Color(0xFFE6A11F)
        RuggednessGrade.HARD -> Color(0xFFE8613C)
        RuggednessGrade.VERY_HARD -> Color(0xFFC4314B)
    }
    return if (g == RuggednessGrade.VERY_HARD) base to Color.White
    else if (dark) lerp(base, cs.surface, 0.55f) to lerp(base, Color.White, 0.6f)
    else lerp(base, cs.surface, 0.78f) to lerp(base, Color.Black, 0.55f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpxCompareScreen(
    entries: List<GpxCompareEntry>,
    isLoading: Boolean,
    maxFiles: Int,
    onPickFiles: () -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onNavigateBack: () -> Unit,
    /** 하단 탭으로 열 때는 뒤로 아이콘을 숨김 */
    isTopLevelTab: Boolean = false
) {
    val loaded = entries.filterIsInstance<GpxCompareEntry.Loaded>()
    // 내 코스로만 채워졌으면 '코스 비교', GPX 파일이 섞이면 'GPX 길 비교'
    val title = if (entries.isNotEmpty() && loaded.size == entries.size &&
        loaded.all { it.source == CompareSource.COURSE }) "코스 비교" else "GPX 길 비교"
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (!isTopLevelTab) IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로") }
                },
                actions = { if (entries.isNotEmpty()) TextButton(onClick = onClear) { Text("모두 지우기") } }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (entries.isEmpty()) IntroCard(maxFiles, onPickFiles)
            else {
                FileList(entries, loaded, maxFiles, onPickFiles, onRemove)
                if (loaded.size == 1) HintText("비교하려면 하나 더 불러오거나 코스를 골라 주세요.")
                if (loaded.isNotEmpty()) {
                    if (loaded.size >= 2) VerdictCard(loaded)
                    val withEle = loaded.filter { it.result.hasElevation }
                    if (withEle.isNotEmpty()) SectionCard("고도 프로파일", "가로: 거리(km) · 세로: 고도(m)") { ElevationChart(loaded) }
                    SectionCard("한눈에 비교", null) { MetricTable(loaded) }
                    if (withEle.isNotEmpty()) SectionCard("경사 분포", "길 전체에서 각 경사가 차지하는 거리 비율") { GradeDistribution(loaded) }
                }
                FormulaCard()
            }
            PrivacyNote()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun IntroCard(maxFiles: Int, onPick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Terrain, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) }
            Spacer(Modifier.height(14.dp))
            Text("어느 길이 더 험할까?", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "GPX 파일을 2~${maxFiles}개 불러오면 거리, 오르막·내리막, 경사, 고도 그래프를 나란히 비교하고 '쉬움'부터 '매우 험함'까지 등급을 매겨 드려요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "코스 목록에서 코스를 골라 들어오면 파일 없이 바로 비교할 수 있어요.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onPick, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Outlined.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("GPX 파일 불러오기")
            }
        }
    }
}

@Composable
private fun FileList(entries: List<GpxCompareEntry>, loaded: List<GpxCompareEntry.Loaded>, maxFiles: Int, onPick: () -> Unit, onRemove: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.forEach { e ->
            val idx = loaded.indexOf(e)
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    when (e) {
                        is GpxCompareEntry.Loaded -> {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(CompareColors[idx % CompareColors.size]))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(e.result.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false))
                                    if (e.source == CompareSource.COURSE) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                                            Text("코스", style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                                        }
                                    }
                                }
                                Text(
                                    if (e.result.hasElevation) "%.2f km · 상승 %,d m".format(e.result.distanceM / 1000, e.result.ascentM.roundToInt())
                                    else "%.2f km · 고도 정보 없음".format(e.result.distanceM / 1000),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (e.result.hasElevation) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFB0611A)
                                )
                            }
                            GradeBadge(e.result.grade)
                        }
                        is GpxCompareEntry.Failed -> {
                            Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(e.message, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    IconButton(onClick = { onRemove(e.id) }) { Icon(Icons.Outlined.Close, contentDescription = "빼기", modifier = Modifier.size(18.dp)) }
                }
            }
        }
        if (entries.size < maxFiles) {
            OutlinedButton(onClick = onPick, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.FileOpen, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text("GPX 추가 (${entries.size}/$maxFiles)")
            }
        }
    }
}

@Composable
internal fun GradeBadge(g: RuggednessGrade) {
    val (bg, fg) = gradeColor(g)
    Surface(shape = RoundedCornerShape(50), color = bg) {
        Text(g.labelKo, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun HintText(t: String) = Text(t, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun VerdictCard(loaded: List<GpxCompareEntry.Loaded>) {
    val sorted = loaded.sortedByDescending { it.result.score }
    val top = sorted.first(); val easy = sorted.last()
    val ratio = if (easy.result.score > 0) top.result.score / easy.result.score else 1.0
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("가장 험한 길", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(CompareColors[loaded.indexOf(top) % 4]))
                Spacer(Modifier.width(8.dp))
                Text(top.result.displayName, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp)); GradeBadge(top.result.grade)
            }
            Spacer(Modifier.height(10.dp))
            // 점수 막대
            val maxScore = max(1.0, sorted.first().result.score)
            loaded.forEachIndexed { i, e ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    Text(e.result.displayName, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.width(92.dp))
                    Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = 0.55f))) {
                        Box(Modifier.fillMaxWidth((e.result.score / maxScore).toFloat().coerceIn(0.02f, 1f)).height(10.dp)
                            .clip(RoundedCornerShape(5.dp)).background(CompareColors[i % 4]))
                    }
                    Text("%.1f".format(e.result.score), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.width(44.dp).padding(start = 8.dp))
                }
            }
            if (sorted.size >= 2 && ratio >= 1.1) {
                Spacer(Modifier.height(6.dp))
                Text("'${easy.result.displayName}'보다 약 %.1f배 힘든 길이에요.".format(ratio),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
internal fun SectionCard(title: String, subtitle: String?, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
internal fun ElevationChart(loaded: List<GpxCompareEntry.Loaded>) {
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val single = MaterialTheme.colorScheme.primary
    val cc = CompareColors
    val withEle = loaded.withIndex().filter { it.value.result.hasElevation }
    val maxKm = withEle.maxOf { it.value.result.distanceM } / 1000.0
    val minE = withEle.minOf { it.value.result.minElevationM!! }
    val maxE = withEle.maxOf { it.value.result.maxElevationM!! }
    val eStep = niceStep((maxE - minE).coerceAtLeast(20.0) / 4)
    val yLo = floor(minE / eStep) * eStep
    val yHi = max(yLo + eStep, ceil(maxE / eStep) * eStep)
    val kStep = niceStep(maxKm.coerceAtLeast(0.5) / 4)
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val left = 40.dp.toPx(); val bottom = 20.dp.toPx(); val top = 6.dp.toPx(); val right = 6.dp.toPx()
        val w = size.width - left - right; val h = size.height - top - bottom
        fun x(km: Double) = left + (km / maxKm * w).toFloat()
        fun y(e: Double) = top + ((yHi - e) / (yHi - yLo) * h).toFloat()
        var e = yLo
        while (e <= yHi + 1e-6) {
            drawLine(gridColor, Offset(left, y(e)), Offset(left + w, y(e)), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            val t = measurer.measure("${e.roundToInt()}", labelStyle)
            drawText(t, topLeft = Offset(left - t.size.width - 6.dp.toPx(), y(e) - t.size.height / 2))
            e += eStep
        }
        var k = 0.0
        while (k <= maxKm + 1e-6) {
            val t = measurer.measure(if (kStep < 1) "%.1f".format(k) else "${k.roundToInt()}", labelStyle)
            drawText(t, topLeft = Offset((x(k) - t.size.width / 2).coerceIn(left - 4, size.width - t.size.width.toFloat()), top + h + 4.dp.toPx()))
            k += kStep
        }
        withEle.forEach { (i, entry) ->
            val prof = entry.result.profile
            val path = Path().apply {
                prof.forEachIndexed { j, p -> if (j == 0) moveTo(x(p.distanceKm), y(p.elevationM)) else lineTo(x(p.distanceKm), y(p.elevationM)) }
            }
            drawPath(path, if (loaded.size == 1) single else cc[i % 4], style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
    Spacer(Modifier.height(8.dp))
    Legend(loaded)
    if (withEle.size < loaded.size) {
        Spacer(Modifier.height(6.dp))
        Text("고도 정보가 없는 파일은 그래프에서 빠졌어요.", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB0611A))
    }
}

@Composable
private fun Legend(loaded: List<GpxCompareEntry.Loaded>) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        loaded.forEachIndexed { i, e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(14.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (loaded.size == 1) MaterialTheme.colorScheme.primary else CompareColors[i % 4]))
                Spacer(Modifier.width(5.dp))
                Text(e.result.displayName, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

private fun niceStep(raw: Double): Double {
    val mag = Math.pow(10.0, floor(Math.log10(raw)))
    val n = raw / mag
    return mag * when { n <= 1 -> 1.0; n <= 2 -> 2.0; n <= 2.5 -> 2.5; n <= 5 -> 5.0; else -> 10.0 }
}

@Composable
private fun MetricTable(loaded: List<GpxCompareEntry.Loaded>) {
    data class Row_(val label: String, val values: List<String>, val worst: Int?)
    fun worstIdx(sel: (RuggednessResult) -> Double?): Int? {
        val v = loaded.map { if (it.result.hasElevation) sel(it.result) else null }
        if (loaded.size < 2 || v.count { it != null } < 2) return null
        return v.withIndex().filter { it.value != null }.maxByOrNull { it.value!! }?.index
    }
    fun ele(r: RuggednessResult, f: (RuggednessResult) -> String) = if (r.hasElevation) f(r) else "—"
    val rows = listOf(
        Row_("난이도 점수", loaded.map { "%.1f".format(it.result.score) }, if (loaded.size > 1) loaded.indices.maxBy { loaded[it].result.score } else null),
        Row_("총거리", loaded.map { "%.2f km".format(it.result.distanceM / 1000) }, if (loaded.size > 1) loaded.indices.maxBy { loaded[it].result.distanceM } else null),
        Row_("누적 상승", loaded.map { ele(it.result) { r -> "%,d m".format(r.ascentM.roundToInt()) } }, worstIdx { it.ascentM }),
        Row_("누적 하강", loaded.map { ele(it.result) { r -> "%,d m".format(r.descentM.roundToInt()) } }, worstIdx { it.descentM }),
        Row_("최고 / 최저", loaded.map { ele(it.result) { r -> "${r.maxElevationM!!.roundToInt()} / ${r.minElevationM!!.roundToInt()}" } }, null),
        Row_("최대 오르막", loaded.map { ele(it.result) { r -> "%.0f%%".format(r.maxUphillGradePct) } }, worstIdx { it.maxUphillGradePct }),
        Row_("최대 내리막", loaded.map { ele(it.result) { r -> "%.0f%%".format(r.maxDownhillGradePct) } }, worstIdx { it.maxDownhillGradePct }),
        Row_("평균 경사", loaded.map { ele(it.result) { r -> "%.1f%%".format(r.avgAbsGradePct) } }, worstIdx { it.avgAbsGradePct }),
        Row_("가파른 구간", loaded.map { ele(it.result) { r -> "%.0f%% · %.1fkm".format(r.steepRatio * 100, r.steepDistanceM / 1000) } }, worstIdx { it.steepDistanceM }),
        Row_("등가거리", loaded.map { "%.1f km".format(it.result.equivalentKm) }, null),
    )
    Column {
        Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(84.dp))
            loaded.forEachIndexed { i, e ->
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(CompareColors[i % 4]))
                    Spacer(Modifier.width(4.dp))
                    Text(e.result.displayName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        rows.forEachIndexed { ri, r ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (ri % 2 == 0) MaterialTheme.colorScheme.surface.copy(alpha = 0.55f) else Color.Transparent)
                    .padding(vertical = 7.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(r.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(78.dp))
                r.values.forEachIndexed { i, v ->
                    val worst = r.worst == i
                    Text(v, style = MaterialTheme.typography.bodyMedium.copy(fontSize = if (loaded.size >= 4) 12.sp else 14.sp),
                        fontWeight = if (worst) FontWeight.Bold else FontWeight.Normal,
                        color = if (worst) CompareColors[i % 4] else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("굵은 색 글씨 = 그 항목에서 가장 힘든 길 · 가파른 구간 = 경사 ${GpxRuggedness.STEEP_PCT.toInt()}% 이상",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GradeDistribution(loaded: List<GpxCompareEntry.Loaded>) {
    val withEle = loaded.withIndex().filter { it.value.result.hasElevation }
    val labels = withEle.first().value.result.gradeBuckets.map { it.labelKo }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        withEle.forEach { (i, e) ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(CompareColors[i % 4]))
                    Spacer(Modifier.width(6.dp))
                    Text(e.result.displayName, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp))) {
                    e.result.gradeBuckets.forEachIndexed { bi, b ->
                        if (b.ratio > 0.001) Box(Modifier.weight(b.ratio.toFloat()).height(16.dp).background(BucketColors[bi]))
                    }
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            labels.forEachIndexed { bi, l ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(BucketColors[bi]))
                    Spacer(Modifier.width(4.dp)); Text(l, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun FormulaCard() {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))) {
        Column(Modifier.padding(16.dp)) {
            Text("난이도 점수는 이렇게 계산해요", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("점수 = 거리(km) + 오르막(m) ÷ 100 + 가파른 구간(km) × 2", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "• 100m 오르는 힘 ≈ 평지 1km 걷는 힘으로 봐요 (등가거리).\n" +
                    "• 경사 ${GpxRuggedness.STEEP_PCT.toInt()}% 이상 구간은 1km마다 2점을 더해요.\n" +
                    "• GPS 고도 잡음은 ${GpxRuggedness.SMOOTH_WINDOW_M.toInt()}m 평균으로 다듬고, ${GpxRuggedness.HYSTERESIS_M.toInt()}m 미만 출렁임은 세지 않아요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(RuggednessGrade.EASY to "~6", RuggednessGrade.MODERATE to "6~12", RuggednessGrade.SOMEWHAT_HARD to "12~18",
                    RuggednessGrade.HARD to "18~28", RuggednessGrade.VERY_HARD to "28~").forEach { (g, range) ->
                    val (bg, fg) = gradeColor(g)
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(bg).padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(g.labelKo, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(range, color = fg, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyNote() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
        Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text("파일은 읽기만 해요. 기록·메모·사진·시간은 저장하거나 동기화하지 않아요.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
