package kr.co.nuni.gildam.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import kr.co.nuni.gildam.data.model.TransportType
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kr.co.nuni.gildam.data.local.entity.ActivityDetailBundle
import kr.co.nuni.gildam.data.local.entity.TrackPointEntity
import kr.co.nuni.gildam.data.local.entity.WaypointWithPhoto
import kr.co.nuni.gildam.data.model.UriStatus
import kr.co.nuni.gildam.map.GildamMapLibreView
import kr.co.nuni.gildam.recording.LatLonPoint
import java.io.File

/**
 * 기록 상세 화면 (설계서 4.2 텍스트 와이어 완벽 구현):
 * - 케이스 A: 현재 기기에 사진이 정상 존재할 때 (로컬 썸네일 / MediaStore URI 표시)
 * - 케이스 B: 다른 기기에서 열람했을 때 ("사진은 촬영한 기기(○○)에만 있어요" 안내 카드)
 * - 케이스 C: 이 기기에서 찍었으나 갤러리에서 사진이 삭제되었거나 권한이 해제된 때 (메모는 100% 유지 + 다시 연결/사진 표시 지우기)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(
    bundle: ActivityDetailBundle?,
    trackPoints: List<TrackPointEntity>,
    onVerifyPhotos: (ActivityDetailBundle) -> Unit,
    onRelinkMissingPhoto: (photoId: String, newUri: Uri) -> Unit,
    onRemoveMissingPhotoKeepMemo: (waypointId: String, photoId: String) -> Unit,
    onNavigateToSafeExport: (String) -> Unit,
    onNavigateBack: () -> Unit,
    /** 예: "약 1시간 10분" (Tobler 추정, 개인 보정). null 이면 숨김 */
    sameRouteEta: String? = null,
    isLoggedIn: Boolean = false,
    onEditActivity: (id: String, title: String, type: TransportType) -> Unit = { _, _, _ -> },
    onDeleteActivity: (String) -> Unit = {},
    onUploadActivity: (String) -> Unit = {}
) {
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    RecordEditHost(
        editing = bundle?.activity?.takeIf { showEdit },
        deleting = bundle?.activity?.takeIf { showDelete },
        onEdit = onEditActivity,
        onDelete = { id -> onDeleteActivity(id); onNavigateBack() },
        onDismissEdit = { showEdit = false }, onDismissDelete = { showDelete = false }
    )
    var relinkingPhotoId by remember { mutableStateOf<String?>(null) }

    val relinkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val targetId = relinkingPhotoId
        if (uri != null && targetId != null) {
            onRelinkMissingPhoto(targetId, uri)
        }
        relinkingPhotoId = null
    }

    LaunchedEffect(bundle) {
        if (bundle != null) {
            onVerifyPhotos(bundle)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(bundle?.activity?.title ?: "기록 상세") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    if (bundle != null) {
                        RecordActionButtons(bundle.activity, isLoggedIn, onEdit = { showEdit = true }, onDelete = { showDelete = true },
                            onUpload = { onUploadActivity(bundle.activity.id) })
                        IconButton(onClick = { onNavigateToSafeExport(bundle.activity.id) }) {
                            Icon(Icons.Outlined.IosShare, contentDescription = "안전 공유 및 GPX 내보내기")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (bundle == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
            ) {
                Text("기록을 불러오는 중입니다...")
            }
            return@Scaffold
        }

        val activity = bundle.activity
        val latLonList = remember(trackPoints) {
            trackPoints.map { LatLonPoint(it.latitude, it.longitude, it.altitudeMeters, it.isPauseBoundary) }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            item {
                // 비공개 기본 상태 및 동기화 배지
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${activity.visibility.labelKo} · ${activity.syncState.labelKo} · ${activity.transportType.labelKo}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 상단 전체 궤적 MapLibre 뷰
                GildamMapLibreView(
                    trackPoints = latLonList,
                    currentLocation = latLonList.lastOrNull(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                )

                // 핵심 통계 요약 바
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DetailMetricColumn("총 거리", String.format("%.2f km", activity.distanceMeters / 1000.0))
                        DetailMetricColumn("총 시간", formatDurationHms(activity.totalDurationMs))
                        DetailMetricColumn("이동 평균", String.format("%.1f km/h", activity.movingAvgSpeedMps * 3.6f))
                        DetailMetricColumn("상승 고도", String.format("+%.0f m", activity.elevationGainMeters))
                    }
                }

                sameRouteEta?.let { eta ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(androidx.compose.material.icons.Icons.Outlined.Schedule, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("같은 길이면 약 $eta 걸려요", style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("오르막·내리막을 반영한 걷기 예상 시간", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "길 위의 이야기 (Waypoints · ${bundle.waypoints.size})",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            if (bundle.waypoints.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "조용히 길에만 집중한 여정이네요. 남겨진 위치 메모가 없습니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            } else {
                items(bundle.waypoints, key = { it.waypoint.id }) { item ->
                    WaypointStoryCard(
                        item = item,
                        onRequestRelinkPhoto = { photoId ->
                            relinkingPhotoId = photoId
                            relinkPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onRemoveMissingPhotoKeepMemo = {
                            item.localPhoto?.photoId?.let { pid ->
                                onRemoveMissingPhotoKeepMemo(item.waypoint.id, pid)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailMetricColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun WaypointStoryCard(
    item: WaypointWithPhoto,
    onRequestRelinkPhoto: (photoId: String) -> Unit,
    onRemoveMissingPhotoKeepMemo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wpt = item.waypoint
    val localPhoto = item.localPhoto

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 사진 상태 분기 (케이스 A / 케이스 B / 케이스 C)
            when {
                // 케이스 B: 사진이 연결된 Waypoint이나 현재 기기에 로컬 PhotoEntity가 없을 때 (다른 기기에서 동기화됨)
                wpt.hasPhoto && localPhoto == null -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.DevicesOther,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "사진은 촬영한 기기(${wpt.capturedDeviceName ?: "다른 기기"})에만 있어요",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "클라우드에 사진 파일을 올리지 않아 이 기기에서는 메모만 보입니다.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // 케이스 C: 이 기기에서 찍은 사진이나 갤러리에서 삭제되었거나 권한이 해제되었을 때
                localPhoto != null && localPhoto.uriStatus != UriStatus.VALID -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.BrokenImage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (localPhoto.uriStatus == UriStatus.MISSING_IN_GALLERY) {
                                        "갤러리에서 사진이 삭제되었거나 위치가 바뀌었어요"
                                    } else {
                                        "갤러리 사진 접근 권한이 필요해요"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onRequestRelinkPhoto(localPhoto.photoId) }) {
                                    Text("갤러리에서 다시 연결")
                                }
                                TextButton(onClick = onRemoveMissingPhotoKeepMemo) {
                                    Text("사진 표시 지우기(메모 유지)")
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // 케이스 A: 현재 기기에 사진이 정상 존재할 때
                localPhoto != null && localPhoto.uriStatus == UriStatus.VALID -> {
                    val modelToLoad: Any = localPhoto.thumbnailLocalPath
                        ?.let { File(it) }
                        ?.takeIf { it.exists() }
                        ?: Uri.parse(localPhoto.mediaStoreUri)

                    AsyncImage(
                        model = modelToLoad,
                        contentDescription = wpt.caption ?: "길 위의 사진",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    val caption = wpt.caption
                    if (!caption.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = caption,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // 메모 본문 (항상 보존)
            Text(
                text = wpt.memoText,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = String.format(
                    "📍 %.4f, %.4f %s",
                    wpt.latitude,
                    wpt.longitude,
                    wpt.altitudeMeters?.let { "· ${it.toInt()}m" } ?: ""
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
