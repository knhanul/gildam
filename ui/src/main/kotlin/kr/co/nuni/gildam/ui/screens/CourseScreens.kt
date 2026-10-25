package kr.co.nuni.gildam.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.co.nuni.gildam.data.course.HikingTimeEstimator
import kr.co.nuni.gildam.data.gpxcompare.RuggednessGrade
import kr.co.nuni.gildam.data.gpxcompare.RuggednessResult
import kr.co.nuni.gildam.map.GildamMapLibreView
import kr.co.nuni.gildam.recording.LatLonPoint
import kr.co.nuni.gildam.ui.theme.WarningAmber
import kotlin.math.cos

data class CourseListItem(
    val id: String, val name: String, val distanceM: Double, val ascentM: Double,
    val grade: RuggednessGrade, val etaSec: Double, val thumb: List<LatLonPoint>
)

data class CourseDetailUi(
    val id: String, val name: String, val result: RuggednessResult, val route: List<LatLonPoint>,
    val etaSec: Double, val paceFactor: Double?
)

data class CourseFollowUi(
    val name: String, val route: List<LatLonPoint>, val totalM: Double, val remainingM: Double,
    val etaSec: Double, val arrivalClock: String, val isOffCourse: Boolean, val offsetM: Double?
)

internal fun kmText(m: Double) = if (m >= 10_000) "%.0f km".format(m / 1000) else "%.1f km".format(m / 1000)

// ---------------- 코스 목록 (탭) ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseListScreen(
    courses: List<CourseListItem>,
    isImporting: Boolean,
    onImport: () -> Unit,
    onOpenCourse: (String) -> Unit,
    onOpenCompare: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("코스") },
                actions = { if (courses.isNotEmpty()) TextButton(onClick = onImport) {
                    Icon(Icons.Outlined.FileOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("GPX 불러오기")
                } }
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { CompareEntryCard(enabled = true, onClick = onOpenCompare) }
            if (isImporting) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (courses.isEmpty()) item { EmptyCourses(onImport) }
            else {
                item {
                    Text("내 코스 ${courses.size}", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
                }
                items(courses, key = { it.id }) { CourseCard(it) { onOpenCourse(it.id) } }
            }
        }
    }
}

@Composable
private fun CompareEntryCard(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(kr.co.nuni.gildam.ui.illustration.brandGradient()), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.CompareArrows, null, tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("GPX 비교", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("여러 길의 험함을 한눈에 비교해요", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun EmptyCourses(onImport: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, start = 4.dp, end = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        kr.co.nuni.gildam.ui.illustration.EmptyCoursesIllustration()
        Spacer(Modifier.height(20.dp))
        Text("아직 코스가 없어요", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("GPX 파일을 불러오면 거리·오르막·난이도와\n예상 소요 시간을 알려드려요.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onImport, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)) {
            Icon(Icons.Outlined.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("GPX 불러오기")
        }
        Spacer(Modifier.height(10.dp))
        Text("좌표만 읽고, 파일 속 시간·이름 같은 개인 정보는 저장하지 않아요.", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CourseCard(c: CourseListItem, onClick: () -> Unit) {
    Card(
        onClick = onClick, shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RouteThumb(c.thumb, Modifier.size(76.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp)); GradeBadge(c.grade)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MiniStat("거리", kmText(c.distanceM))
                    MiniStat("오르막", "%.0f m".format(c.ascentM))
                    MiniStat("예상", HikingTimeEstimator.formatKo(c.etaSec))
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) = Column {
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

/** 코스 모양 썸네일 (지도 없이 선만) */
@Composable
internal fun RouteThumb(points: List<LatLonPoint>, modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.surface
    val line = MaterialTheme.colorScheme.primary
    val start = MaterialTheme.colorScheme.secondary
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(bg)) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            if (points.size < 2) return@Canvas
            val minLat = points.minOf { it.latitude }; val maxLat = points.maxOf { it.latitude }
            val minLon = points.minOf { it.longitude }; val maxLon = points.maxOf { it.longitude }
            val kx = cos(Math.toRadians((minLat + maxLat) / 2))
            val sx = ((maxLon - minLon) * kx).coerceAtLeast(1e-7); val sy = (maxLat - minLat).coerceAtLeast(1e-7)
            val sc = minOf(size.width / sx, size.height / sy).toFloat()
            val ox = (size.width - sx.toFloat() * sc) / 2; val oy = (size.height - sy.toFloat() * sc) / 2
            fun o(p: LatLonPoint) = Offset(ox + ((p.longitude - minLon) * kx).toFloat() * sc, oy + (maxLat - p.latitude).toFloat() * sc)
            val path = Path().apply { points.forEachIndexed { i, p -> val q = o(p); if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y) } }
            drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(start, 3.5.dp.toPx(), o(points.first()))
        }
    }
}

// ---------------- 코스 상세 ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    detail: CourseDetailUi?,
    onNavigateBack: () -> Unit,
    onFollow: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.name ?: "코스", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로") } },
                actions = { if (detail != null) IconButton(onClick = { onDelete(detail.id) }) { Icon(Icons.Outlined.DeleteOutline, "코스 삭제") } }
            )
        },
        bottomBar = {
            if (detail != null) Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                kr.co.nuni.gildam.ui.illustration.VividActionButton(
                    title = "이 코스 따라 걷기",
                    subtitle = "예상 " + HikingTimeEstimator.formatKo(detail.etaSec) + " · 남은 거리와 도착 시간을 알려드려요",
                    type = kr.co.nuni.gildam.data.model.TransportType.HIKE,
                    onClick = { onFollow(detail.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    ) { inner ->
        if (detail == null) { Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) { Text("코스를 불러오는 중…") }; return@Scaffold }
        val r = detail.result
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GildamMapLibreView(
                trackPoints = emptyList(), currentLocation = null, coursePoints = detail.route, fitToTrack = true,
                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(22.dp))
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                GradeBadge(r.grade); Spacer(Modifier.width(10.dp))
                Text(r.grade.descriptionKo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // 예상 시간 히어로
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("예상 소요 시간", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("약 " + HikingTimeEstimator.formatKo(detail.etaSec), style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (detail.paceFactor != null) "오르막·내리막 반영 · 내 걷기 기록으로 보정(×%.2f)".format(detail.paceFactor)
                        else "오르막·내리막을 반영한 보통 걸음 기준 (쉬는 시간 제외)",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("거리", kmText(r.distanceM), Modifier.weight(1f))
                StatTile("오르막", if (r.hasElevation) "%.0f m".format(r.ascentM) else "–", Modifier.weight(1f))
                StatTile("최고 고도", r.maxElevationM?.let { "%.0f m".format(it) } ?: "–", Modifier.weight(1f))
            }
            if (r.hasElevation) {
                SectionCard("고도 프로파일", "가로: 거리(km) · 세로: 고도(m)") { ElevationChart(listOf(GpxCompareEntry.Loaded(detail.id, r))) }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = WarningAmber, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text("이 GPX에는 고도 정보가 없어 평지로 계산했어요.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("난이도 점수 %.1f · 가파른 구간(15%% 이상) %.1f km".format(r.score, r.steepDistanceM / 1000),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

// ---------------- 기록 화면: 따라 걷기 카드 ----------------

@Composable
fun FollowCourseCard(f: CourseFollowUi, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val off = f.isOffCourse
    val bg = if (off) WarningAmber else MaterialTheme.colorScheme.primary
    val fg = if (off) Color.White else MaterialTheme.colorScheme.onPrimary
    Surface(shape = RoundedCornerShape(18.dp), color = bg, shadowElevation = 6.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (off) Icons.Outlined.WarningAmber else Icons.Outlined.Route, null, tint = fg, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (off) "코스에서 %.0f m 벗어났어요".format(f.offsetM ?: 0.0) else "따라 걷는 중 · ${f.name}",
                    style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                TextButton(onClick = onStop, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("그만", color = fg) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                FollowStat("남은 거리", kmText(f.remainingM), fg)
                FollowStat("남은 시간", HikingTimeEstimator.formatKo(f.etaSec), fg)
                FollowStat("도착 예정", f.arrivalClock, fg)
            }
            val progress = if (f.totalM > 0) (1 - f.remainingM / f.totalM).toFloat().coerceIn(0f, 1f) else 0f
            Spacer(Modifier.height(10.dp))
            Box(Modifier.padding(end = 10.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(fg.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxWidth(progress).height(6.dp).clip(RoundedCornerShape(3.dp)).background(fg))
            }
        }
    }
}

@Composable
private fun FollowStat(label: String, value: String, fg: Color) = Column {
    Text(label, style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.8f))
    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = fg)
}
