package kr.co.nuni.gildam.ui.screens

import androidx.compose.ui.res.pluralStringResource
import kr.co.nuni.gildam.ui.l10n.*
import androidx.compose.ui.res.stringResource
import kr.co.nuni.gildam.ui.R
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInFull
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
import kr.co.nuni.gildam.data.elevation.ElevationAccumulator
import kr.co.nuni.gildam.data.gpx.TrackGapDetector
import kr.co.nuni.gildam.data.local.entity.ActivityDetailBundle
import kr.co.nuni.gildam.data.local.entity.TrackPointEntity
import kr.co.nuni.gildam.data.local.entity.WaypointWithPhoto
import kr.co.nuni.gildam.data.model.UriStatus
import kr.co.nuni.gildam.map.GildamMapLibreView
import kr.co.nuni.gildam.recording.LatLonPoint
import java.io.File
import kotlin.math.roundToInt

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
    isLoggedIn: Boolean = false,
    onEditActivity: (id: String, title: String, type: TransportType) -> Unit = { _, _, _ -> },
    onDeleteActivity: (String) -> Unit = {},
    onUploadActivity: (String) -> Unit = {},
    /** 기기 사진에서 이 기록과 같은 시간·자리의 사진 찾기 (사진은 기기에만) */
    recordPhotos: kr.co.nuni.gildam.ui.GildamViewModel.RecordPhotosState =
        kr.co.nuni.gildam.ui.GildamViewModel.RecordPhotosState(),
    onFindRecordPhotos: () -> Unit = {}
) {
    // 사진 접근 권한 요청. Android 14+ 는 전체/일부(선택한 사진만) 중 고를 수 있게 두 권한을 함께 요청.
    // 결과와 상관없이 다시 찾아 봄 — 허용된 범위의 사진만 보이고, 거절되면 안내 줄이 그대로 남습니다.
    val photoPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { onFindRecordPhotos() }
    val requestPhotoAccess = {
        val permissions = when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
            )
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU -> arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES
            )
            else -> arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        runCatching { photoPermissionLauncher.launch(permissions) }
        Unit
    }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    // 기록 지도 전체 화면 (사진 핀 포함)
    var showFullscreenMap by remember { mutableStateOf(false) }
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
    // 기록·경로가 준비되면 기기 사진 후보를 찾음 (권한 없으면 안내 상태로).
    // 경로 점이 없는 기록(0개)은 시간만으로 맞추므로 바로 찾습니다.
    val photoSearchKey = bundle?.activity?.id to trackPoints.size
    LaunchedEffect(photoSearchKey) {
        val a = bundle?.activity ?: return@LaunchedEffect
        val routeReady = trackPoints.isNotEmpty() || a.distanceMeters <= 0.0
        if (routeReady) onFindRecordPhotos()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(bundle?.activity?.title ?: stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (bundle != null) {
                        RecordActionButtons(bundle.activity, isLoggedIn, onEdit = { showEdit = true }, onDelete = { showDelete = true },
                            onUpload = { onUploadActivity(bundle.activity.id) })
                        IconButton(onClick = { onNavigateToSafeExport(bundle.activity.id) }) {
                            Icon(Icons.Outlined.IosShare, contentDescription = stringResource(R.string.detail_share_cd))
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
                Text(stringResource(R.string.detail_loading))
            }
            return@Scaffold
        }

        val activity = bundle.activity
        val latLonList = remember(trackPoints) {
            trackPoints.map { LatLonPoint(it.latitude, it.longitude, it.altitudeMeters, it.isPauseBoundary) }
        }
        val gapCount = remember(trackPoints) {
            TrackGapDetector.countGaps(trackPoints)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            item {
                // 비공개 기본 상태 및 동기화 배지 (+ 끊긴 구간 배지)
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
                        text = "${activity.visibility.label} · ${activity.syncState.label} · ${activity.transportType.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (gapCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = pluralStringResource(R.plurals.detail_gaps, gapCount, gapCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // 상단 전체 궤적 MapLibre 뷰: 경로 전체가 한 화면에 보이게 맞춤.
                // 경로 점을 다 읽기 전에는 지도를 만들지 않아 서울 기본 위치가 잠깐 보이지 않게 함.
                val mapModifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                if (latLonList.isEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = mapModifier) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                if (activity.distanceMeters > 0.0) stringResource(R.string.detail_route_loading) else stringResource(R.string.detail_no_route),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Box(mapModifier) {
                        GildamMapLibreView(
                            trackPoints = latLonList,
                            currentLocation = null,
                            fitToTrack = true,
                            modifier = Modifier.fillMaxSize()
                        )
                        // 전체 화면으로 보기 (작은 아이콘 + 말풍선 안내)
                        kr.co.nuni.gildam.ui.illustration.TooltipIconButton(
                            Icons.Outlined.OpenInFull, stringResource(R.string.map_fullscreen), { showFullscreenMap = true },
                            style = kr.co.nuni.gildam.ui.illustration.IconButtonStyle.TONAL,
                            visualSize = 34.dp, iconSize = 18.dp,
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                        )
                    }
                    if (showFullscreenMap) {
                        RecordMapFullscreen(
                            title = activity.title,
                            route = latLonList,
                            photos = recordPhotos.photos,
                            onClose = { showFullscreenMap = false }
                        )
                    }
                }

                // 기존 기록(elevationLossMeters == null)은 저장된 경로 점 고도로 한 번 계산해 보고, 못 구하면 null('–')
                val resolvedElevation = remember(activity.elevationGainMeters, activity.elevationLossMeters, trackPoints) {
                    val savedLoss = activity.elevationLossMeters
                    if (savedLoss != null) {
                        val validLoss = savedLoss.takeIf { it >= 0f }
                        val hasGainOrLoss = validLoss != null || activity.elevationGainMeters > 0f
                        if (hasGainOrLoss) activity.elevationGainMeters to validLoss else null to null
                    } else {
                        val computed = ElevationAccumulator.computeFromTrackPoints(trackPoints)
                        if (computed != null) {
                            val gain = if (activity.elevationGainMeters > 0f) activity.elevationGainMeters else computed.first
                            gain to computed.second
                        } else if (activity.elevationGainMeters > 0f) {
                            activity.elevationGainMeters to null
                        } else {
                            null to null
                        }
                    }
                }
                val elevationDetailText = when {
                    resolvedElevation.first == null && resolvedElevation.second == null ->
                        stringResource(R.string.gauge_value_missing)
                    resolvedElevation.second != null ->
                        stringResource(
                            R.string.detail_elevation_pair,
                            (resolvedElevation.first ?: 0f).coerceAtLeast(0f).roundToInt(),
                            "${resolvedElevation.second!!.coerceAtLeast(0f).roundToInt()} m"
                        )
                    else ->
                        stringResource(
                            R.string.detail_elevation_gain_only,
                            (resolvedElevation.first ?: 0f).coerceAtLeast(0f).roundToInt()
                        )
                }

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
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DetailMetricColumn(stringResource(R.string.detail_total_distance), String.format("%.2f km", activity.distanceMeters / 1000.0))
                        DetailMetricColumn(stringResource(R.string.detail_total_time), formatDurationHms(activity.totalDurationMs))
                        DetailMetricColumn(stringResource(R.string.detail_moving_avg), String.format("%.1f km/h", activity.movingAvgSpeedMps * 3.6f))
                        DetailMetricColumn(stringResource(R.string.detail_elevation_up_down), elevationDetailText)
                    }
                }

                bundle.weather?.toWeatherNow()?.takeIf { it.hasDisplayableData() }?.let { weather ->
                    RecordWeatherCard(
                        weather = weather,
                        transportType = activity.transportType,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // 이 기록과 같은 시간·자리에 찍힌 기기 사진 (사진은 기기에만, URI 만 참조)
                RecordPhotoSection(
                    loading = recordPhotos.loading,
                    permissionDenied = recordPhotos.permissionDenied,
                    photos = recordPhotos.photos,
                    truncated = recordPhotos.truncated,
                    onRequestPermission = requestPhotoAccess
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.detail_waypoints_title, bundle.waypoints.size),
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
                            text = stringResource(R.string.detail_waypoints_empty),
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
            maxLines = 1
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
                                    text = stringResource(R.string.detail_photo_other_device, wpt.capturedDeviceName ?: stringResource(R.string.detail_photo_other_device_unknown)),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = stringResource(R.string.detail_photo_other_device_desc),
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
                                        stringResource(R.string.detail_photo_missing)
                                    } else {
                                        stringResource(R.string.detail_photo_permission)
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onRequestRelinkPhoto(localPhoto.photoId) }) {
                                    Text(stringResource(R.string.detail_photo_relink))
                                }
                                TextButton(onClick = onRemoveMissingPhotoKeepMemo) {
                                    Text(stringResource(R.string.detail_photo_unlink))
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
                        contentDescription = wpt.caption ?: stringResource(R.string.detail_photo_cd),
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
