package kr.co.nuni.gildam.ui.screens

import kr.co.nuni.gildam.ui.l10n.*
import androidx.compose.ui.res.pluralStringResource
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
import androidx.compose.material3.HorizontalDivider
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
import kr.co.nuni.gildam.data.list.ActivityPlaceInfo
import kr.co.nuni.gildam.data.list.RecordGroupMode
import kr.co.nuni.gildam.data.list.RecordGrouper
import kr.co.nuni.gildam.data.list.RecordListFilter
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
    regions: Map<String, String?> = emptyMap(),
    /** activity_id → 출발·도착 지명 정보 */
    places: Map<String, ActivityPlaceInfo> = emptyMap(),
    /** 아주 짧은 기록(0.1 km 미만 또는 1분 미만) 숨기기 */
    hideShortRecords: Boolean = false,
    onToggleHideShortRecords: (Boolean) -> Unit = {},
    onDeleteShortActivities: (List<String>) -> Unit = {}
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
    var showDeleteShortConfirm by rememberSaveable { mutableStateOf(false) }

    val (hiddenShortActivities, visibleActivities) = remember(activities, hideShortRecords) {
        if (hideShortRecords) {
            activities.partition { RecordListFilter.isShortRecord(it) }
        } else {
            emptyList<ActivityEntity>() to activities
        }
    }

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
    if (showDeleteShortConfirm && hiddenShortActivities.isNotEmpty()) {
        DeleteShortRecordsDialog(
            activities = hiddenShortActivities,
            onConfirm = {
                val ids = hiddenShortActivities.map { it.id }
                onDeleteShortActivities(ids)
                Toast.makeText(
                    context,
                    context.resources.getQuantityString(R.plurals.delete_short_done_toast, ids.size, ids.size),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { showDeleteShortConfirm = false }
        )
    }
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

            // 활동 필터(전체 + 활동 아이콘 5개)와 묶어 보기(최근순·월별·지역별 + 짧은 기록 숨기기)를 한 줄에
            RecordFilterRow(
                selectedTransportFilter = selectedTransportFilter,
                onSelectTransportFilter = onSelectTransportFilter,
                recordGroupMode = recordGroupMode,
                onSelectRecordGroup = onSelectRecordGroup,
                hideShortRecords = hideShortRecords,
                onToggleHideShortRecords = onToggleHideShortRecords
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (merging) {
                Text(
                    stringResource(R.string.merge_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
            } else if (hiddenShortActivities.isNotEmpty()) {
                HiddenShortRecordsBar(
                    count = hiddenShortActivities.size,
                    onShow = { onToggleHideShortRecords(false) },
                    onDeleteAll = { showDeleteShortConfirm = true },
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            if (visibleActivities.isEmpty()) {
                if (hiddenShortActivities.isEmpty()) {
                    BoxEmptyListState(onStartClick = onNavigateBackToRecorder)
                }
            } else {
                // 검색·이동수단·짧은 기록 필터가 끝난 목록을 그대로 묶습니다 (필터 → 그룹)
                val groups = remember(visibleActivities, regions, recordGroupMode) {
                    RecordGrouper.group(visibleActivities, regions, recordGroupMode)
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
                                placeInfo = places[item.id],
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
    onSelectRecordGroup: (RecordGroupMode) -> Unit,
    hideShortRecords: Boolean = false,
    onToggleHideShortRecords: (Boolean) -> Unit = {}
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
        RecordGroupModeMenu(
            recordGroupMode = recordGroupMode,
            onSelectRecordGroup = onSelectRecordGroup,
            hideShortRecords = hideShortRecords,
            onToggleHideShortRecords = onToggleHideShortRecords
        )
    }
}

/** 지금 묶어 보는 방식만 보이는 작은 버튼 + 세 가지 선택 메뉴 + 짧은 기록 숨기기 토글 */
@Composable
internal fun RecordGroupModeMenu(
    recordGroupMode: RecordGroupMode,
    onSelectRecordGroup: (RecordGroupMode) -> Unit,
    hideShortRecords: Boolean = false,
    onToggleHideShortRecords: (Boolean) -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val active = recordGroupMode != RecordGroupMode.RECENT || hideShortRecords
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
                },
                hideShortRecords = hideShortRecords,
                onToggleHideShortRecords = {
                    expanded = false
                    onToggleHideShortRecords(!hideShortRecords)
                }
            )
        }
    }
}

/** 묶어 보기 메뉴의 세 줄 + 짧은 기록 숨기기 토글 */
@Composable
internal fun RecordGroupMenuItems(
    recordGroupMode: RecordGroupMode,
    onSelect: (RecordGroupMode) -> Unit,
    hideShortRecords: Boolean = false,
    onToggleHideShortRecords: (() -> Unit)? = null
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
    if (onToggleHideShortRecords != null) {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.hide_short_records_title), style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = {
                if (hideShortRecords) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                } else {
                    Spacer(modifier = Modifier.size(18.dp))
                }
            },
            onClick = onToggleHideShortRecords
        )
    }
}

/** 짧은 기록이 숨겨져 있을 때 목록 상단에 뜨는 안내 바: 숨긴 개수 + 보이기 + 모아서 지우기 */
@Composable
internal fun HiddenShortRecordsBar(
    count: Int,
    onShow: () -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cs.surfaceVariant.copy(alpha = 0.7f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = pluralStringResource(R.plurals.short_records_hidden_count, count, count),
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onShow,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text(stringResource(R.string.short_records_show), style = MaterialTheme.typography.labelMedium)
            }
            TextButton(
                onClick = onDeleteAll,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text(
                    stringResource(R.string.short_records_delete_all),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.error
                )
            }
        }
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
 * 리스트(목록) 보기 한 줄:
 * - 기본 제목인 경우: 출발→도착 지명(없으면 출발→도착 시각) + 통계(시각·거리·소요·이동 평균 속도)
 * - 커스텀 제목인 경우: 제목 + 출발→도착 지명(있을 때) + 통계
 * - 우측: 작은 클라우드 상태 아이콘 + 오버플로(⋮) 메뉴. 길게 누르면 합치기 모드.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ActivityCompactRow(
    activity: ActivityEntity,
    onClick: () -> Unit,
    placeInfo: ActivityPlaceInfo? = null,
    isLoggedIn: Boolean = false,
    /** 합칠 기록 고르기 모드면 선택 여부, 아니면 null */
    mergeSelected: Boolean? = null,
    onLongClick: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onUpload: () -> Unit = {},
    zoneId: ZoneId = ZoneId.systemDefault()
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val locale = currentLocale()
    val customTitle = customRecordTitleOrNull(context, activity, zoneId)
    val arrowFmt = stringResource(R.string.record_route_places)
    val placeLine = placeInfo?.displayPlaces { s, e -> String.format(locale, arrowFmt, s, e) }

    val endMs = activity.endedAtEpochMs ?: (activity.startedAtEpochMs + activity.totalDurationMs.coerceAtLeast(0L))
    val timeRange = stringResource(
        R.string.record_time_range,
        formatShortTime(context, activity.startedAtEpochMs, zoneId),
        formatShortTime(context, endMs, zoneId)
    )
    val distStr = String.format(locale, "%.2f", activity.distanceMeters / 1000.0)
    val durStr = formatCompactDuration(activity.totalDurationMs)
    val speedStr = String.format(locale, "%.1f", computeDisplaySpeedKmh(activity))
    val fullStats = stringResource(R.string.record_row_stats, timeRange, distStr, durStr, speedStr)
    val statsWithoutTime = stringResource(R.string.record_row_stats_no_time, distStr, durStr, speedStr)
    val tabularStatsStyle = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (mergeSelected == true) cs.primaryContainer else cs.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 12.dp, end = if (mergeSelected == null) 4.dp else 12.dp, top = 9.dp, bottom = 9.dp),
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
        TransportSticker(type = activity.transportType, selected = true, size = 24.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            when {
                customTitle != null -> {
                    Text(
                        text = customTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (placeLine != null) {
                        Text(
                            text = placeLine,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = cs.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = fullStats,
                        style = tabularStatsStyle,
                        color = cs.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                placeLine != null -> {
                    Text(
                        text = placeLine,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = fullStats,
                        style = tabularStatsStyle,
                        color = cs.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                else -> {
                    Text(
                        text = timeRange,
                        style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = statsWithoutTime,
                        style = tabularStatsStyle,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = if (activity.syncState == SyncState.SYNCED) Icons.Outlined.CloudDone else Icons.Outlined.CloudQueue,
            contentDescription = activity.syncState.label,
            tint = if (activity.syncState == SyncState.SYNCED) cs.primary.copy(alpha = 0.85f) else cs.onSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.size(15.dp)
        )
        if (mergeSelected == null) {
            RecordOverflowMenu(
                activity = activity,
                isLoggedIn = isLoggedIn,
                onEdit = onEdit,
                onDelete = onDelete,
                onUpload = onUpload
            )
        }
    }
}

internal fun computeDisplaySpeedKmh(activity: ActivityEntity): Double {
    val mps = when {
        activity.movingAvgSpeedMps > 0f -> activity.movingAvgSpeedMps.toDouble()
        activity.movingDurationMs > 0L && activity.distanceMeters > 0.0 ->
            activity.distanceMeters / (activity.movingDurationMs / 1000.0)
        activity.avgSpeedMps > 0f -> activity.avgSpeedMps.toDouble()
        activity.totalDurationMs > 0L && activity.distanceMeters > 0.0 ->
            activity.distanceMeters / (activity.totalDurationMs / 1000.0)
        else -> 0.0
    }
    return mps * 3.6
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
