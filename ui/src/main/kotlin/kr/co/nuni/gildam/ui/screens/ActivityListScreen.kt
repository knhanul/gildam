package kr.co.nuni.gildam.ui.screens

import kr.co.nuni.gildam.ui.l10n.*
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.sp
import kr.co.nuni.gildam.ui.R
import kr.co.nuni.gildam.ui.illustration.GildamTooltip
import kr.co.nuni.gildam.ui.illustration.TransportSticker
import kr.co.nuni.gildam.ui.illustration.brandGradient
import kr.co.nuni.gildam.ui.illustration.onBrand
import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.list.RecordGroupMode
import kr.co.nuni.gildam.data.list.RecordGrouper
import kr.co.nuni.gildam.data.merge.RecordMerger
import kr.co.nuni.gildam.data.model.ActivityStatus
import kr.co.nuni.gildam.data.model.SyncState
import kr.co.nuni.gildam.data.model.TransportType
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ActivityListScreen(
    activities: List<ActivityEntity>,
    selectedTransportFilter: TransportType?,
    searchQuery: String,
    onSelectTransportFilter: (TransportType?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectActivity: (String) -> Unit,
    onNavigateBackToRecorder: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToGpxCompare: () -> Unit = {},
    /** 하단 탭바가 있을 때는 뒤로/설정 아이콘을 숨김 */
    isTopLevelTab: Boolean = false,
    isLoggedIn: Boolean = false,
    onEditActivity: (id: String, title: String, type: TransportType) -> Unit = { _, _, _ -> },
    onDeleteActivity: (String) -> Unit = {},
    onUploadActivity: (String) -> Unit = {},
    /** 고른 기록들을 하나로 합치기 (합치는 동안 결과를 기다림). 성공하면 true */
    onMergeActivities: suspend (List<String>, String) -> Boolean = { _, _ -> false },
    /** 최근순 / 월별 / 지역별 묶어 보기 */
    recordGroupMode: RecordGroupMode = RecordGroupMode.RECENT,
    onSelectRecordGroup: (RecordGroupMode) -> Unit = {},
    /** activity_id → 지역 이름 (없으면 '지역 확인 중', null 이면 '지역 정보 없음') */
    regions: Map<String, String?> = emptyMap()
) {
    // 찾기: 기본은 가려져 있고 앱바 돋보기 아이콘으로 펼침/접음
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(searchExpanded) {
        if (searchExpanded) searchFocusRequester.requestFocus()
    }

    // 수정 시트 / 삭제 확인 대화상자
    var editingId by remember { mutableStateOf<String?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }

    // 합치기: 카드를 길게 누르면 여러 개 고르는 모드로 들어갑니다
    var merging by rememberSaveable { mutableStateOf(false) }
    var mergeSelectedCsv by rememberSaveable { mutableStateOf("") }
    var showMergeConfirm by rememberSaveable { mutableStateOf(false) }
    var mergeRunning by remember { mutableStateOf(false) }
    val mergeSelected = remember(mergeSelectedCsv) {
        mergeSelectedCsv.split(',').filter { it.isNotEmpty() }
    }
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun toggleMerge(item: ActivityEntity) {
        // 기록 중이거나 끝나지 않은 기록은 합칠 수 없으니 고를 때부터 막습니다
        if (item.status != ActivityStatus.COMPLETED) {
            Toast.makeText(context, context.getString(R.string.merge_only_finished), Toast.LENGTH_SHORT).show()
            return
        }
        val next = if (item.id in mergeSelected) mergeSelected - item.id
        else if (mergeSelected.size < RecordMerger.MAX_COUNT) mergeSelected + item.id
        else mergeSelected
        mergeSelectedCsv = next.joinToString(",")
    }
    fun exitMerging() { merging = false; mergeSelectedCsv = ""; showMergeConfirm = false }

    if (showMergeConfirm) {
        val sources = activities.filter { it.id in mergeSelected }
        Dialog(
            onDismissRequest = { if (!mergeRunning) showMergeConfirm = false }
        ) {
            MergeRecordsContent(
                sources = sources,
                defaultTitle = RecordMerger.defaultTitle(sources).ifBlank { stringResource(R.string.merge_default_title) },
                merging = mergeRunning,
                onCancel = { showMergeConfirm = false },
                onConfirm = { title ->
                    mergeRunning = true
                    scope.launch {
                        val ok = onMergeActivities(sources.map { it.id }, title)
                        mergeRunning = false
                        showMergeConfirm = false
                        if (ok) {
                            exitMerging()
                            Toast.makeText(context, context.resources.getQuantityString(R.plurals.merge_done_toast, sources.size, sources.size), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, context.getString(R.string.merge_failed_toast), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }
    }
    RecordEditHost(
        editing = activities.firstOrNull { it.id == editingId },
        deleting = activities.firstOrNull { it.id == deletingId },
        onEdit = onEditActivity, onDelete = onDeleteActivity,
        onDismissEdit = { editingId = null }, onDismissDelete = { deletingId = null }
    )
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateBackToRecorder,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.list_new_record)) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp)
        ) {
            // 컴팩트 상단 헤더 (길담 보관함 타이틀 + 우측 찾기 아이콘)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isTopLevelTab) {
                        IconButton(
                            onClick = onNavigateBackToRecorder,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.list_back_to_record))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Image(
                        painter = painterResource(R.drawable.gildam_logo),
                        contentDescription = stringResource(R.string.logo_cd),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = if (merging) stringResource(R.string.merge_pick_title) else stringResource(R.string.list_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (merging) {
                        TextButton(onClick = { exitMerging() }) { Text(stringResource(R.string.action_cancel)) }
                        Button(
                            onClick = { showMergeConfirm = true },
                            enabled = mergeSelected.size >= RecordMerger.MIN_COUNT
                        ) {
                            Text(stringResource(R.string.merge_action) + if (mergeSelected.isEmpty()) "" else " (${mergeSelected.size})")
                        }
                    }
                    if (!merging) GildamTooltip(stringResource(R.string.merge_records_tooltip)) {
                        IconButton(
                            onClick = {
                                merging = true
                                searchExpanded = false
                                onSearchQueryChange("")
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.CallMerge, contentDescription = stringResource(R.string.merge_records_tooltip), modifier = Modifier.size(20.dp))
                        }
                    }
                    if (!merging) IconButton(
                        onClick = {
                            searchExpanded = !searchExpanded
                            if (!searchExpanded) onSearchQueryChange("")
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            if (searchExpanded) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = if (searchExpanded) stringResource(R.string.search_close) else stringResource(R.string.search),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (!isTopLevelTab) {
                        IconButton(onClick = onNavigateToGpxCompare, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.Terrain, contentDescription = stringResource(R.string.gpx_compare_title), modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onNavigateToSettings, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            AnimatedVisibility(visible = searchExpanded) {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text(stringResource(R.string.search_placeholder), style = MaterialTheme.typography.bodyMedium) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.search_clear))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFocusRequester)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // 활동 필터(전체 + 활동 아이콘 5개)와 묶어 보기(최근순·월별·지역별)를 한 줄에
            RecordFilterRow(
                selectedTransportFilter = selectedTransportFilter,
                onSelectTransportFilter = onSelectTransportFilter,
                recordGroupMode = recordGroupMode,
                onSelectRecordGroup = onSelectRecordGroup
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (merging) {
                Text(
                    stringResource(R.string.merge_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
            }

            if (activities.isEmpty()) {
                BoxEmptyListState(onStartClick = onNavigateBackToRecorder)
            } else {
                // 검색·이동수단 필터가 끝난 목록을 그대로 묶습니다 (필터 → 그룹)
                val groups = remember(activities, regions, recordGroupMode) {
                    RecordGrouper.group(activities, regions, recordGroupMode)
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    groups.forEach { group ->
                        if (group.title.isNotEmpty()) {
                            stickyHeader(key = "group:${group.key}") {
                                RecordGroupHeader(title = group.displayTitle(), count = group.activities.size)
                            }
                        }
                        items(group.activities, key = { it.id }) { item ->
                            val onRowClick: () -> Unit = {
                                if (merging) toggleMerge(item) else onSelectActivity(item.id)
                            }
                            val onRowLongClick: () -> Unit = {
                                if (!merging && item.status == ActivityStatus.COMPLETED) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    merging = true
                                    toggleMerge(item)
                                }
                            }
                            ActivityCompactRow(
                                activity = item,
                                onClick = onRowClick,
                                isLoggedIn = isLoggedIn,
                                mergeSelected = if (merging) (item.id in mergeSelected) else null,
                                onLongClick = onRowLongClick,
                                onEdit = { editingId = item.id },
                                onDelete = { deletingId = item.id },
                                onUpload = { onUploadActivity(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 활동 필터(전체 + 활동 아이콘 5개)와 묶어 보기(최근순·월별·지역별)를 한 줄에 둡니다.
 * - 왼쪽: 지금처럼 가로로 밀어 보는 활동 칩 (좁은 화면에서도 잘리지 않음)
 * - 오른쪽 끝: 지금 묶어 보는 방식만 보이는 작은 버튼 → 누르면 세 가지가 담긴 메뉴가 열립니다
 */
@Composable
internal fun RecordFilterRow(
    selectedTransportFilter: TransportType?,
    onSelectTransportFilter: (TransportType?) -> Unit,
    recordGroupMode: RecordGroupMode,
    onSelectRecordGroup: (RecordGroupMode) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val allSelected = selectedTransportFilter == null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(50))
                    .then(
                        if (allSelected) {
                            Modifier
                                .background(brandGradient())
                                .border(1.5.dp, cs.primary, RoundedCornerShape(50))
                        } else {
                            Modifier
                                .background(cs.surfaceVariant)
                                .border(1.dp, cs.outlineVariant, RoundedCornerShape(50))
                        }
                    )
                    .clickable { onSelectTransportFilter(null) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.filter_all),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (allSelected) onBrand() else cs.onSurfaceVariant
                )
            }

            TransportType.entries.forEach { type ->
                val selected = selectedTransportFilter == type
                GildamTooltip(type.label) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .then(
                                if (selected) {
                                    Modifier.border(2.dp, cs.primary, CircleShape)
                                } else {
                                    Modifier.border(1.dp, cs.outlineVariant, CircleShape)
                                }
                            )
                            .clickable {
                                onSelectTransportFilter(if (selected) null else type)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        TransportSticker(type = type, selected = selected, size = 34.dp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        RecordGroupModeMenu(recordGroupMode = recordGroupMode, onSelectRecordGroup = onSelectRecordGroup)
    }
}

/** 지금 묶어 보는 방식만 보이는 작은 버튼 + 세 가지 선택 메뉴 */
@Composable
internal fun RecordGroupModeMenu(
    recordGroupMode: RecordGroupMode,
    onSelectRecordGroup: (RecordGroupMode) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val active = recordGroupMode != RecordGroupMode.RECENT
    Box {
        Row(
            modifier = Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(50))
                .then(
                    if (active) {
                        Modifier
                            .background(brandGradient())
                            .border(1.5.dp, cs.primary, RoundedCornerShape(50))
                    } else {
                        Modifier
                            .background(cs.surfaceVariant)
                            .border(1.dp, cs.outlineVariant, RoundedCornerShape(50))
                    }
                )
                .clickable { expanded = true }
                .padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = recordGroupMode.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) onBrand() else cs.onSurfaceVariant
            )
            Icon(
                Icons.Outlined.ArrowDropDown,
                contentDescription = stringResource(R.string.group_mode_pick_cd),
                tint = if (active) onBrand() else cs.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RecordGroupMenuItems(
                recordGroupMode = recordGroupMode,
                onSelect = { mode ->
                    expanded = false
                    onSelectRecordGroup(mode)
                }
            )
        }
    }
}

/** 묶어 보기 메뉴의 세 줄 (지금 고른 방식에만 체크 표시) */
@Composable
internal fun RecordGroupMenuItems(
    recordGroupMode: RecordGroupMode,
    onSelect: (RecordGroupMode) -> Unit
) {
    RecordGroupMode.entries.forEach { mode ->
        DropdownMenuItem(
            text = { Text(mode.label, style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = {
                if (mode == recordGroupMode) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                } else {
                    Spacer(modifier = Modifier.size(18.dp))
                }
            },
            onClick = { onSelect(mode) }
        )
    }
}

/** 묶음 헤더: '2026년 11월' / '서울 종로구' 처럼 작은 글씨 + 그 묶음의 기록 수 */
@Composable
internal fun RecordGroupHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.group_count, count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 리스트(목록) 보기 한 줄: 제목 + 거리·시간 + 클라우드 상태 + 수정/삭제/올리기.
 * 카드와 동작은 같습니다 — 길게 누르면 합치기 선택 모드, 합치기 모드에서는 액션 버튼 숨김.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ActivityCompactRow(
    activity: ActivityEntity,
    onClick: () -> Unit,
    isLoggedIn: Boolean = false,
    /** 합칠 기록 고르기 모드면 선택 여부, 아니면 null */
    mergeSelected: Boolean? = null,
    onLongClick: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onUpload: () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val stats = String.format(
        "%.2f km · %s",
        activity.distanceMeters / 1000.0,
        formatCompactDuration(activity.totalDurationMs)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (mergeSelected == true) cs.primaryContainer else cs.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (mergeSelected != null) {
            Icon(
                imageVector = if (mergeSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (mergeSelected) stringResource(R.string.merge_selected_cd) else stringResource(R.string.merge_unselected_cd),
                tint = if (mergeSelected) cs.primary else cs.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        TransportSticker(type = activity.transportType, selected = true, size = 20.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = activity.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stats,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = if (activity.syncState == SyncState.SYNCED) Icons.Outlined.CloudDone else Icons.Outlined.CloudQueue,
            contentDescription = activity.syncState.label,
            tint = cs.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        if (mergeSelected == null) {
            RecordActionButtons(
                activity, isLoggedIn,
                onEdit = onEdit, onDelete = onDelete, onUpload = onUpload
            )
        }
    }
}

/** 한 줄 목록에 들어갈 짧은 시간 표기: 1시간 미만 "38:12", 그 이상 "2:05:30" */
internal fun formatCompactDuration(durationMs: Long): String {
    val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}

@Composable
private fun BoxEmptyListState(onStartClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            kr.co.nuni.gildam.ui.illustration.EmptyRecordsIllustration()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.list_empty_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.list_empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
