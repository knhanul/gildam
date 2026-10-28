package kr.co.nuni.gildam.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudQueue
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    isTopLevelTab: Boolean = false,
    isLoggedIn: Boolean = false,
    onEditActivity: (id: String, title: String, type: TransportType) -> Unit = { _, _, _ -> },
    onDeleteActivity: (String) -> Unit = {},
    onUploadActivity: (String) -> Unit = {}
) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    RecordEditHost(
        editing = activities.firstOrNull { it.id == editingId },
        deleting = activities.firstOrNull { it.id == deletingId },
        onEdit = onEditActivity, onDelete = onDeleteActivity,
        onDismissEdit = { editingId = null }, onDismissDelete = { deletingId = null }
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("길담 보관함") },
                navigationIcon = {
                    if (!isTopLevelTab) {
                        IconButton(onClick = onNavigateBackToRecorder) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "기록 화면으로")
                        }
                    }
                },
                actions = {
                    if (!isTopLevelTab) {
                        IconButton(onClick = onNavigateToGpxCompare) {
                            Icon(Icons.Outlined.Terrain, contentDescription = "GPX 길 비교")
                        }
                    }
                    if (!isTopLevelTab) {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = "설정")
                        }
                    }
                }
            )
        },
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
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("제목, 장소 검색...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(
                    selected = selectedTransportFilter == null,
                    onClick = { onSelectTransportFilter(null) },
                    label = { Text("전체") }
                )
                TransportType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedTransportFilter == type,
                        onClick = { onSelectTransportFilter(type) },
                        label = { Text(type.labelKo) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (activities.isEmpty()) {
                BoxEmptyListState(onStartClick = onNavigateBackToRecorder)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(activities, key = { it.id }) { item ->
                        ActivitySummaryCard(
                            activity = item,
                            onClick = { onSelectActivity(item.id) },
                            isLoggedIn = isLoggedIn,
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

@Composable
internal fun ActivitySummaryCard(
    activity: ActivityEntity,
    onClick: () -> Unit,
    isLoggedIn: Boolean = false,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onUpload: () -> Unit = {}
) {
    val dateStr = DateTimeFormatter.ofPattern("yyyy. MM. dd (E) a hh:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(activity.startedAtEpochMs))

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = activity.transportType.labelKo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                CloudStatusChip(CloudStatus.of(activity.syncState))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = activity.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                RecordActionButtons(activity, isLoggedIn, onEdit = onEdit, onDelete = onDelete, onUpload = onUpload)
            }
            Text(
                text = dateStr,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = String.format("거리 %.2f km", activity.distanceMeters / 1000.0),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "시간 ${formatDurationHms(activity.totalDurationMs)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = String.format("이동평균 %.1f km/h", activity.movingAvgSpeedMps * 3.6f),
                    style = MaterialTheme.typography.bodyMedium
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
