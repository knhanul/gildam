package kr.co.nuni.gildam.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.nuni.gildam.ui.R
import kr.co.nuni.gildam.ui.illustration.GildamTooltip
import kr.co.nuni.gildam.ui.illustration.TransportSticker
import kr.co.nuni.gildam.ui.illustration.brandGradient
import kr.co.nuni.gildam.ui.illustration.onBrand
import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.model.SyncState
import kr.co.nuni.gildam.data.model.TransportType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
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
    isTopLevelTab: Boolean = false
) {
    // 찾기: 기본은 가려져 있고 앱바 돋보기 아이콘으로 펼침/접음
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(searchExpanded) {
        if (searchExpanded) searchFocusRequester.requestFocus()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateBackToRecorder,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("새 길 기록하기") }
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
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "기록 화면으로")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Image(
                        painter = painterResource(R.drawable.gildam_logo),
                        contentDescription = "길담 로고",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = "길담 보관함",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            searchExpanded = !searchExpanded
                            if (!searchExpanded) onSearchQueryChange("")
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            if (searchExpanded) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = if (searchExpanded) "찾기 닫기" else "찾기",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (!isTopLevelTab) {
                        IconButton(onClick = onNavigateToGpxCompare, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.Terrain, contentDescription = "GPX 길 비교", modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = onNavigateToSettings, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.Settings, contentDescription = "설정", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            AnimatedVisibility(visible = searchExpanded) {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("제목, 장소 검색...", style = MaterialTheme.typography.bodyMedium) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Outlined.Clear, contentDescription = "검색어 지우기")
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

            // 활동 필터: 전체 + 기록 버튼의 활동 아이콘 5개
            val cs = MaterialTheme.colorScheme
            val allSelected = selectedTransportFilter == null
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 2.dp)
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
                        text = "전체",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (allSelected) onBrand() else cs.onSurfaceVariant
                    )
                }

                TransportType.entries.forEach { type ->
                    val selected = selectedTransportFilter == type
                    GildamTooltip(type.labelKo) {
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

            Spacer(modifier = Modifier.height(6.dp))

            if (activities.isEmpty()) {
                BoxEmptyListState(onStartClick = onNavigateBackToRecorder)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(activities, key = { it.id }) { item ->
                        ActivitySummaryCard(
                            activity = item,
                            onClick = { onSelectActivity(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivitySummaryCard(
    activity: ActivityEntity,
    onClick: () -> Unit
) {
    val dateStr = DateTimeFormatter.ofPattern("yy.MM.dd (E) HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(activity.startedAtEpochMs))

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TransportSticker(
                    type = activity.transportType,
                    selected = true,
                    size = 22.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = activity.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (activity.syncState == SyncState.SYNCED) {
                        Icons.Outlined.CloudDone
                    } else {
                        Icons.Outlined.CloudQueue
                    },
                    contentDescription = activity.syncState.labelKo,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = String.format(
                        "%.2f km · %s",
                        activity.distanceMeters / 1000.0,
                        formatDurationHms(activity.totalDurationMs)
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
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
                text = "첫 번째 길이야기를 시작해 보세요",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "스마트폰만으로 걸음과 드라이브 궤적, 길 위의 메모와 사진을 기록할 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
