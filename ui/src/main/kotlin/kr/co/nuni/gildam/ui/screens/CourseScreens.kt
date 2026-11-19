package kr.co.nuni.gildam.ui.screens

import kr.co.nuni.gildam.ui.l10n.*
import androidx.compose.ui.res.stringResource
import kr.co.nuni.gildam.ui.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.outlined.History
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kr.co.nuni.gildam.data.course.RecordCourseBlock
import kr.co.nuni.gildam.data.gpxcompare.GpxParseException
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalBottomSheet
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
import kr.co.nuni.gildam.data.gpxcompare.RuggednessGrade
import kr.co.nuni.gildam.data.gpxcompare.RuggednessResult
import kr.co.nuni.gildam.map.GildamMapLibreView
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.recording.LatLonPoint
import kr.co.nuni.gildam.ui.theme.WarningAmber
import kotlin.math.cos

data class CourseListItem(
    val id: String, val name: String, val distanceM: Double, val ascentM: Double,
    val grade: RuggednessGrade, val thumb: List<LatLonPoint>
)

data class CourseDetailUi(
    val id: String,
    val name: String,
    val result: RuggednessResult,
    val route: List<LatLonPoint>,
    val reversedResult: RuggednessResult = result,
    val reversedRoute: List<LatLonPoint> = route.asReversed()
)

data class CourseFollowUi(
    val name: String,
    val route: List<LatLonPoint>,
    val totalM: Double,
    val remainingM: Double,
    val isOffCourse: Boolean,
    val offsetM: Double?,
    val reversed: Boolean = false
)

/** '내 기록에서' 목록 한 줄. [block] 이 있으면 고를 수 없고 그 이유를 보여 줌 */
data class RecordCourseChoice(
    val id: String,
    val title: String,
    val startedAtEpochMs: Long,
    val distanceM: Double,
    val transportType: TransportType,
    val block: RecordCourseBlock? = null
)

/** 코스 탭 아래 안내(스낵바)로 보여 줄 결과 */
sealed interface CourseMessage {
    data class Imported(val count: Int) : CourseMessage
    data class ImportFailed(val imported: Int, val reasons: List<GpxParseException.Reason>) : CourseMessage
    data class FromRecords(val created: Int, val skipped: Int) : CourseMessage
    data class FromRecordsBlocked(val reason: RecordCourseBlock) : CourseMessage
}

internal fun kmText(m: Double) = if (m >= 10_000) "%.0f km".format(m / 1000) else "%.1f km".format(m / 1000)

// ---------------- 코스 목록 (탭) ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseListScreen(
    courses: List<CourseListItem>,
    isImporting: Boolean,
    onImport: () -> Unit,
    onOpenCourse: (String) -> Unit,
    onOpenCompare: () -> Unit,
    /** 코스 목록에서 골라 바로 비교 (고른 순서 그대로 코스 id 전달) */
    onCompareCourses: (List<String>) -> Unit = {},
    /** 한 번에 비교할 수 있는 코스 수 (GPX 파일 비교와 같은 값) */
    maxCompare: Int = 4,
    /** '내 기록에서' 목록을 열 때 불러올 기록들 (null 이면 '파일에서'만 바로 엶) */
    recordChoices: (() -> Flow<List<RecordCourseChoice>>)? = null,
    /** 고른 기록들로 코스 만들기 */
    onCreateFromRecords: (List<String>) -> Unit = {},
    /** 불러오기 결과 안내 */
    messages: Flow<CourseMessage> = emptyFlow()
) {
    // 'GPX 불러오기' → 파일에서 / 내 기록에서
    var showImportChooser by rememberSaveable { mutableStateOf(false) }
    var showRecordPicker by rememberSaveable { mutableStateOf(false) }
    val openImport: () -> Unit = { if (recordChoices == null) onImport() else showImportChooser = true }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(messages) {
        messages.collect { m -> snackbarHostState.showSnackbar(courseMessageText(context, m)) }
    }
    if (showImportChooser) {
        ModalBottomSheet(onDismissRequest = { showImportChooser = false }) {
            ImportSourceContent(
                onFromFile = { showImportChooser = false; onImport() },
                onFromRecords = { showImportChooser = false; showRecordPicker = true }
            )
        }
    }
    if (showRecordPicker && recordChoices != null) {
        val choices by remember { recordChoices() }.collectAsState(initial = null)
        var pickedCsv by rememberSaveable { mutableStateOf("") }
        val picked = remember(pickedCsv) { pickedCsv.split(',').filter { it.isNotEmpty() }.toSet() }
        ModalBottomSheet(
            onDismissRequest = { showRecordPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            RecordPickerContent(
                choices = choices,
                selected = picked,
                onToggle = { id -> pickedCsv = (if (id in picked) picked - id else picked + id).joinToString(",") },
                onConfirm = {
                    val ids = choices.orEmpty().map { it.id }.filter { it in picked }
                    showRecordPicker = false
                    pickedCsv = ""
                    onCreateFromRecords(ids)
                },
                onCancel = { showRecordPicker = false; pickedCsv = "" }
            )
        }
    }
    // 선택 모드: 코스를 눌러 고르고 상단 '비교'로 넘어감. 길게 눌러도 시작
    var selecting by rememberSaveable { mutableStateOf(false) }
    // 고른 코스 id 를 쉼표로 이어 저장 (화면 회전에도 유지)
    var selectedCsv by rememberSaveable { mutableStateOf("") }
    val selected = remember(selectedCsv) { selectedCsv.split(',').filter { it.isNotEmpty() } }
    val haptics = LocalHapticFeedback.current

    fun toggle(id: String) {
        val next = if (id in selected) selected - id else if (selected.size < maxCompare) selected + id else selected
        selectedCsv = next.joinToString(",")
    }

    fun exitSelection() { selecting = false; selectedCsv = "" }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text(stringResource(R.string.course_compare_title, selected.size, maxCompare)) },
                    navigationIcon = {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.selection_cancel))
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { val ids = selected.toList(); exitSelection(); onCompareCourses(ids) },
                            enabled = selected.size >= 2
                        ) {
                            Text(stringResource(R.string.compare) + if (selected.isNotEmpty()) " (${selected.size})" else "")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.courses_title)) },
                    actions = {
                        if (courses.size >= 2) kr.co.nuni.gildam.ui.illustration.TooltipIconButton(
                            Icons.Outlined.CompareArrows, stringResource(R.string.course_pick_compare), { selecting = true },
                            style = kr.co.nuni.gildam.ui.illustration.IconButtonStyle.TONAL, visualSize = 38.dp, iconSize = 20.dp,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        if (courses.isNotEmpty()) kr.co.nuni.gildam.ui.illustration.TooltipIconButton(
                            Icons.AutoMirrored.Outlined.NoteAdd, stringResource(R.string.course_import_gpx), openImport,
                            style = kr.co.nuni.gildam.ui.illustration.IconButtonStyle.BRAND, visualSize = 38.dp, iconSize = 20.dp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                )
            }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!selecting) item { CompareEntryCard(enabled = true, onClick = onOpenCompare) }
            if (isImporting) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (courses.isEmpty()) item { EmptyCourses(openImport) }
            else {
                item {
                    Text(
                        if (selecting) stringResource(R.string.course_pick_hint, maxCompare)
                        else stringResource(R.string.course_my_count, courses.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                    )
                }
                items(courses, key = { it.id }) { c ->
                    CourseCard(
                        c = c,
                        selected = if (selecting) (c.id in selected) else null,
                        onClick = {
                            if (!selecting) onOpenCourse(c.id)
                            else if (c.id in selected || selected.size < maxCompare) toggle(c.id)
                            else haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onLongClick = {
                            if (!selecting) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                selecting = true
                                if (c.id !in selected) toggle(c.id)
                            }
                        }
                    )
                }
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
                Text(stringResource(R.string.gpx_compare_short), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(stringResource(R.string.gpx_compare_card_desc), style = MaterialTheme.typography.bodySmall,
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
        Text(stringResource(R.string.courses_empty_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.courses_empty_desc), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onImport, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)) {
            Icon(Icons.Outlined.FileOpen, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.gpx_import))
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.courses_privacy), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/**
 * 코스 카드. [selected] 가 null 이면 일반(탭=상세), 아니면 선택 모드(체크 표시).
 * 길게 누르면 선택 모드로 들어갑니다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CourseCard(
    c: CourseListItem,
    selected: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val selectedDesc = stringResource(R.string.state_selected)
    val notSelectedDesc = stringResource(R.string.state_not_selected)
    val shape = RoundedCornerShape(20.dp)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected == true) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (selected == true) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics {
                if (selected != null) stateDescription = if (selected) selectedDesc else notSelectedDesc
            }
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected != null) {
                Icon(
                    if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
            }
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
                    MiniStat(stringResource(R.string.stat_distance), kmText(c.distanceM))
                    MiniStat(stringResource(R.string.stat_climb), "%.0f m".format(c.ascentM))
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

// ---------------- GPX 불러오기: 파일에서 / 내 기록에서 ----------------

/** 'GPX 불러오기'를 누르면 나오는 두 갈래 선택 (시트 내용) */
@Composable
internal fun ImportSourceContent(onFromFile: () -> Unit, onFromRecords: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text(stringResource(R.string.gpx_import), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(14.dp))
        ImportSourceRow(Icons.Outlined.FileOpen, stringResource(R.string.course_import_from_file), stringResource(R.string.course_import_from_file_desc), onFromFile)
        Spacer(Modifier.height(10.dp))
        ImportSourceRow(Icons.Outlined.History, stringResource(R.string.course_import_from_records), stringResource(R.string.course_import_from_records_desc), onFromRecords)
    }
}

@Composable
private fun ImportSourceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * '내 기록에서' 고르기 (시트 내용). 여러 개 고를 수 있고,
 * 점이 없거나 너무 짧은 기록은 흐리게 두고 이유를 보여 줍니다.
 * [choices] 가 null 이면 불러오는 중.
 */
@Composable
internal fun RecordPickerContent(
    choices: List<RecordCourseChoice>?,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault()
) {
    val count = choices.orEmpty().count { it.id in selected && it.block == null }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
        Text(stringResource(R.string.course_from_records_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.course_from_records_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        when {
            choices == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            choices.isEmpty() -> Text(
                stringResource(R.string.course_from_records_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(choices, key = { it.id }) { c -> RecordChoiceRow(c, c.id in selected, { onToggle(c.id) }, zoneId) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onConfirm, enabled = count > 0, shape = RoundedCornerShape(14.dp)) {
                Text(stringResource(R.string.course_from_records_make) + if (count > 0) " ($count)" else "")
            }
        }
    }
}

@Composable
private fun RecordChoiceRow(c: RecordCourseChoice, checked: Boolean, onToggle: () -> Unit, zoneId: java.time.ZoneId) {
    val enabled = c.block == null
    val cs = MaterialTheme.colorScheme
    val date = remember(c.startedAtEpochMs, zoneId) {
        java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
            .format(java.time.Instant.ofEpochMilli(c.startedAtEpochMs).atZone(zoneId))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (checked && enabled) cs.primaryContainer else cs.surfaceVariant)
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (checked && enabled) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (checked && enabled) cs.primary else cs.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        kr.co.nuni.gildam.ui.illustration.TransportSticker(c.transportType, selected = true, size = 24.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(c.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$date · ${kmText(c.distanceM)}",
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = cs.onSurfaceVariant,
                maxLines = 1
            )
            c.block?.let { b ->
                Text(b.label, style = MaterialTheme.typography.labelSmall, color = cs.error, maxLines = 1)
            }
        }
    }
}

/** 기록 → 코스를 못 만드는 이유 글자 */
internal val RecordCourseBlock.label: String
    @Composable get() = stringResource(labelRes)

@androidx.annotation.StringRes
internal fun RecordCourseBlock.labelResOf(): Int = labelRes

private val RecordCourseBlock.labelRes: Int
    get() = when (this) {
        RecordCourseBlock.NOT_FINISHED -> R.string.course_block_not_finished
        RecordCourseBlock.NO_POINTS -> R.string.course_block_no_points
        RecordCourseBlock.TOO_SHORT -> R.string.course_block_too_short
    }

private fun importReasonRes(r: GpxParseException.Reason): Int = when (r) {
    GpxParseException.Reason.OPEN_FAILED -> R.string.gpx_open_failed
    GpxParseException.Reason.UNREADABLE -> R.string.gpx_parse_failed
    GpxParseException.Reason.TOO_FEW_POINTS -> R.string.course_import_no_points
}

/** 스낵바 글자 */
internal fun courseMessageText(context: android.content.Context, m: CourseMessage): String = when (m) {
    is CourseMessage.Imported -> context.resources.getQuantityString(R.plurals.course_imported, m.count, m.count)
    is CourseMessage.ImportFailed -> {
        val reason = context.getString(importReasonRes(m.reasons.first()))
        if (m.imported == 0 && m.reasons.size == 1) reason
        else context.getString(R.string.course_import_partial, m.imported, m.reasons.size, reason)
    }
    is CourseMessage.FromRecords -> {
        val made = context.resources.getQuantityString(R.plurals.course_from_records_done, m.created, m.created)
        if (m.skipped > 0) made + " " + context.getString(R.string.course_from_records_skipped, m.skipped) else made
    }
    is CourseMessage.FromRecordsBlocked -> context.getString(m.reason.labelRes)
}

// ---------------- 코스 상세 ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    detail: CourseDetailUi?,
    onNavigateBack: () -> Unit,
    /** 활동 선택 시트에서 고른 활동·방향으로 따라가기 시작 (활동 종류와 역방향 여부 전달) */
    onFollow: (String, TransportType, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    /** 시트를 처음 열 때 미리 골라 둘 활동 (마지막으로 고른 활동 또는 코스로 추정한 값) */
    suggestedActivity: TransportType = TransportType.WALK,
    /** 사용자가 활동을 고르면 기억해 두기 (다음 시트 기본값) */
    onRememberActivity: (TransportType) -> Unit = {},
    /** 미리보기·테스트용 초기 역방향 값 (기본 정방향 false) */
    initialReversed: Boolean = false
) {
    var showFullscreenMap by rememberSaveable { mutableStateOf(false) }
    var showFollowSheet by rememberSaveable { mutableStateOf(false) }
    var reversed by rememberSaveable(detail?.id) { mutableStateOf(initialReversed) }
    // 지도 ↔ 고도 그래프 연결: 고른 코스 지점 거리(km). null 이면 선택 없음
    var selectedKm by rememberSaveable(detail?.id) { mutableStateOf<Double?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.name ?: stringResource(R.string.courses_title_one), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back)) } },
                actions = { if (detail != null) kr.co.nuni.gildam.ui.illustration.TooltipIconButton(
                    Icons.Outlined.DeleteOutline, stringResource(R.string.course_delete), { onDelete(detail.id) },
                    style = kr.co.nuni.gildam.ui.illustration.IconButtonStyle.PLAIN, modifier = Modifier.padding(end = 4.dp)) }
            )
        },
        bottomBar = {
            if (detail != null) Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                kr.co.nuni.gildam.ui.illustration.VividActionButton(
                    title = stringResource(R.string.course_follow),
                    subtitle = stringResource(R.string.course_follow_sub),
                    type = suggestedActivity,
                    onClick = { showFollowSheet = true },
                    // 기기마다 다른 시스템 내비게이션 바(제스처·3버튼) 높이만큼 띄워 버튼이 가려지지 않게 함
                    modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    ) { inner ->
        if (detail == null) { Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) { Text(stringResource(R.string.course_loading)) }; return@Scaffold }
        val r = if (reversed) detail.reversedResult else detail.result
        val activeRoute = if (reversed) detail.reversedRoute else detail.route
        if (showFollowSheet) {
            FollowActivitySheet(
                courseName = detail.name,
                initial = suggestedActivity,
                reversed = reversed,
                onReversedChange = { next ->
                    reversed = next
                    selectedKm = null
                },
                onDismiss = { showFollowSheet = false },
                onStart = { type, rev ->
                    showFollowSheet = false
                    onRememberActivity(type)
                    onFollow(detail.id, type, rev)
                }
            )
        }
        // 전체 화면 지도 (다이얼로그 창이라 스크롤과 무관하게 화면을 덮음)
        if (showFullscreenMap) {
            CourseMapFullscreen(
                name = detail.name,
                route = activeRoute,
                selectedKm = selectedKm,
                onSelectKm = { selectedKm = it },
                onClose = { showFullscreenMap = false }
            )
        }
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(22.dp))) {
                GildamMapLibreView(
                    trackPoints = emptyList(), currentLocation = null, coursePoints = activeRoute, fitToTrack = true,
                    selectedCourseKm = selectedKm,
                    // 코스 선을 누르면 그 지점을 고도 그래프에 표시 (선에서 120m 안쪽 탭만)
                    onCourseTap = { km -> selectedKm = km },
                    modifier = Modifier.fillMaxSize()
                )
                // 전체 화면으로 보기 (작은 아이콘 + 말풍선 안내)
                kr.co.nuni.gildam.ui.illustration.TooltipIconButton(
                    Icons.Outlined.OpenInFull, stringResource(R.string.map_fullscreen), { showFullscreenMap = true },
                    style = kr.co.nuni.gildam.ui.illustration.IconButtonStyle.TONAL,
                    visualSize = 34.dp, iconSize = 18.dp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                )
                // 반대 방향은 지도 오른쪽 아래에 띄운 작은 칩 (켜면 출발 표시·고도 그래프가 바로 뒤집힘)
                ReverseDirectionChip(
                    reversed = reversed,
                    onReversedChange = { next ->
                        reversed = next
                        selectedKm = null
                    },
                    elevated = true,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                GradeBadge(r.grade); Spacer(Modifier.width(10.dp))
                Text(r.grade.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(stringResource(R.string.stat_distance), kmText(r.distanceM), Modifier.weight(1f))
                StatTile(stringResource(R.string.stat_climb), if (r.hasElevation) "%.0f m".format(r.ascentM) else "–", Modifier.weight(1f))
                StatTile(stringResource(R.string.stat_max_elevation), r.maxElevationM?.let { "%.0f m".format(it) } ?: "–", Modifier.weight(1f))
            }
            if (r.hasElevation) {
                SectionCard(
                    stringResource(R.string.elevation_profile),
                    if (selectedKm != null) stringResource(R.string.elevation_hint_selected)
                    else stringResource(R.string.elevation_hint)
                ) {
                    ElevationChart(
                        loaded = listOf(GpxCompareEntry.Loaded(detail.id, r)),
                        selectedDistanceKm = selectedKm,
                        onSelectDistanceKm = { km -> selectedKm = km }
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = WarningAmber, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.course_no_elevation), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(stringResource(R.string.course_score_line).format(r.score, r.steepDistanceM / 1000),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 작은 '⇄ 반대 방향' 칩. 켜면 코스를 도착점→출발점 순서로 봅니다. [elevated] 는 지도 위에 띄울 때(불투명 배경+그림자). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReverseDirectionChip(
    reversed: Boolean,
    onReversedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    elevated: Boolean = false
) {
    val desc = stringResource(R.string.course_reverse_title)
    val stateOn = stringResource(R.string.state_selected)
    val stateOff = stringResource(R.string.state_not_selected)
    val m = modifier
        .heightIn(min = 32.dp)
        .semantics {
            contentDescription = desc
            stateDescription = if (reversed) stateOn else stateOff
        }
    val label: @Composable () -> Unit = {
        Text(stringResource(R.string.course_reverse_chip), style = MaterialTheme.typography.labelLarge)
    }
    val icon: @Composable () -> Unit = {
        Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
    }
    val onClick = { onReversedChange(!reversed) }
    if (elevated) {
        ElevatedFilterChip(selected = reversed, onClick = onClick, label = label, leadingIcon = icon, modifier = m)
    } else {
        FilterChip(selected = reversed, onClick = onClick, label = label, leadingIcon = icon, modifier = m)
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

// ---------------- 기록 화면: 따라가기 띠 (한 줄 알약) ----------------

private val FollowBannerHeight = 44.dp
private val FollowBannerProgressHeight = 3.dp

@Composable
fun FollowCourseCard(f: CourseFollowUi, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val off = f.isOffCourse
    val bg = (if (off) WarningAmber else MaterialTheme.colorScheme.primary).copy(alpha = 0.92f)
    val fg = if (off) Color.White else MaterialTheme.colorScheme.onPrimary
    val remaining = kmText(f.remainingM)
    val reverseLabel = stringResource(R.string.follow_reverse_badge)
    val remainingCd = stringResource(R.string.follow_remaining_value_cd, remaining)
    val bannerCd = buildString {
        if (off) append(stringResource(R.string.follow_off_course).format(f.offsetM ?: 0.0))
        else append(stringResource(R.string.follow_following, f.name))
        if (f.reversed) { append(", "); append(reverseLabel) }
        append(", "); append(remainingCd)
    }
    val stopLabel = stringResource(R.string.follow_stop)
    val progress = if (f.totalM > 0) (1 - f.remainingM / f.totalM).toFloat().coerceIn(0f, 1f) else 0f
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth().height(FollowBannerHeight)
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 2.dp)
                    .align(Alignment.CenterStart)
                    .semantics { contentDescription = bannerCd },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (off) Icons.Outlined.WarningAmber else Icons.Outlined.Route,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    f.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (f.reversed) {
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(50), color = fg.copy(alpha = 0.22f)) {
                        Text(
                            text = reverseLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = fg,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    remaining,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                    maxLines = 1
                )
                kr.co.nuni.gildam.ui.illustration.GildamTooltip(stopLabel) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onStop)
                            .semantics { contentDescription = stopLabel },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(FollowBannerProgressHeight).background(fg.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxWidth(progress).height(FollowBannerProgressHeight).background(fg))
            }
        }
    }
}

/**
 * 코스 지도 전체 화면. 같은 지도를 화면 가득 띄우고, 두 손가락 회전·확대·이동과 나침반을 허용합니다.
 * 닫기는 오른쪽 위 작은 X 버튼 (뒤로 가기도 닫기로 동작).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseMapFullscreen(
    name: String,
    route: List<LatLonPoint>,
    selectedKm: Double?,
    onSelectKm: (Double) -> Unit,
    onClose: () -> Unit
) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                GildamMapLibreView(
                    trackPoints = emptyList(),
                    currentLocation = null,
                    coursePoints = route,
                    fitToTrack = true,
                    fullscreenGestures = true,
                    // 전체 화면에서도 같은 선택 지점을 보여 주고, 여기서 탭해 고를 수도 있음
                    selectedCourseKm = selectedKm,
                    onCourseTap = onSelectKm,
                    modifier = Modifier.fillMaxSize()
                )
                // 코스 이름 + 닫기 (지도 위 작은 오버레이)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), Color.Transparent)
                            )
                        )
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    kr.co.nuni.gildam.ui.illustration.TooltipIconButton(
                        Icons.Outlined.Close, stringResource(R.string.map_close), onClose,
                        style = kr.co.nuni.gildam.ui.illustration.IconButtonStyle.PLAIN,
                        visualSize = 36.dp, iconSize = 20.dp
                    )
                }
                // 두 손가락으로 돌려 보라는 짧은 안내
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 26.dp)
                ) {
                    Text(
                        stringResource(R.string.map_gesture_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * '어떤 활동으로 따라갈까요?' 시트.
 * 작은 활동 아이콘 5개(길게 누르면 이름 말풍선) 중 하나를 고르고 '이 활동으로 출발'을 누르면 시작.
 * 선택한 활동은 기록의 활동 종류가 되고, 다음에는 기본값으로 기억됩니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FollowActivitySheet(
    courseName: String,
    initial: TransportType,
    reversed: Boolean = false,
    onReversedChange: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
    onStart: (TransportType, Boolean) -> Unit
) {
    var selected by rememberSaveable { mutableStateOf(initial.name) }
    val type = TransportType.entries.firstOrNull { it.name == selected } ?: TransportType.WALK

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        courseName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(R.string.follow_pick_activity),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                kr.co.nuni.gildam.ui.illustration.GildamTooltip(stringResource(R.string.action_close)) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close), modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            ) {
                TransportType.entries.forEach { t ->
                    val isSel = t == type
                    kr.co.nuni.gildam.ui.illustration.GildamTooltip(t.label) {
                        val tLabel = t.label
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSel) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .then(
                                    if (isSel) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                                    else Modifier
                                )
                                .clickable { selected = t.name }
                                .semantics {
                                    contentDescription = tLabel
                                    role = Role.RadioButton
                                    this.selected = isSel
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            kr.co.nuni.gildam.ui.illustration.TransportSticker(t, selected = isSel, size = 28.dp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            ReverseDirectionChip(
                reversed = reversed,
                onReversedChange = onReversedChange
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onStart(type, reversed) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(stringResource(R.string.follow_start_activity), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
