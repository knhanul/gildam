package kr.co.nuni.gildam.ui.screens

import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.co.nuni.gildam.ui.R
import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.model.GaugeFormat
import kr.co.nuni.gildam.data.model.GaugeMetric
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.map.GildamMapLibreView
import kr.co.nuni.gildam.ui.illustration.TooltipIconButton
import kr.co.nuni.gildam.ui.illustration.IconButtonStyle
import androidx.compose.material.icons.outlined.AddLocationAlt
import kr.co.nuni.gildam.map.MapCameraMode
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.saveable.rememberSaveable
import kr.co.nuni.gildam.ui.illustration.TransportChip
import kr.co.nuni.gildam.ui.illustration.VividActionButton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import kr.co.nuni.gildam.recording.LiveRecordingState
import kr.co.nuni.gildam.ui.theme.TabularStatHeroStyle
import kr.co.nuni.gildam.ui.theme.TabularStatMediumStyle
import kr.co.nuni.gildam.ui.theme.WarningAmber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveRecordingScreen(
    liveState: LiveRecordingState,
    selectedTransport: TransportType,
    onSelectTransport: (TransportType) -> Unit,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onAddWaypoint: (memoText: String, caption: String?, photoUri: android.net.Uri?) -> Unit,
    onNavigateToList: () -> Unit,
    onNavigateToSettings: () -> Unit,
    /** 하단 탭바가 있을 때는 상단 목록/설정 아이콘을 숨김 */
    showTopNavIcons: Boolean = true,
    /** 코스 따라 걷기 중이면 코스 선·남은 거리·도착 예정 표시 */
    follow: CourseFollowUi? = null,
    onStopFollow: () -> Unit = {},
    /** 마지막으로 선택했던 '기록 중 지도 보기' 모드 (DataStore에서 복원) */
    defaultCameraMode: MapCameraMode = MapCameraMode.DEFAULT,
    /** 지도 보기 모드를 바꿀 때 DataStore에 기억시켜 다음 실행에도 유지 */
    onSelectCameraMode: (MapCameraMode) -> Unit = {},
    /** 설정에서 활동 구분별로 고른 '지도 위 계기판' 값 */
    overlayMetric: GaugeMetric = GaugeMetric.defaultFor(selectedTransport),
    /** 설정의 '기록 중 화면 켜 두기' 값 */
    keepScreenOn: Boolean = false,
    /** 앱이 강제 종료되어 남아 있던 미완료 기록 (기록 중이 아닐 때만 표시) */
    unfinishedRecording: ActivityEntity? = null,
    onResumeUnfinishedRecording: () -> Unit = {},
    onFinishUnfinishedRecording: () -> Unit = {},
    /** 테스트·미리보기에서 현재 시각을 고정할 때 사용. null 이면 1초마다 실제 시각으로 갱신 */
    fixedClockMs: Long? = null
) {
    // 기록 중 화면 켜 두기: 켜져 있고 기록 중(일시정지 포함)일 때만 이 화면에서 화면 꺼짐 방지, 벗어나거나 종료 시 해제
    val currentView = LocalView.current
    val shouldKeepScreenOn = keepScreenOn && (liveState.isRecording || liveState.isPaused)
    DisposableEffect(currentView, shouldKeepScreenOn) {
        currentView.keepScreenOn = shouldKeepScreenOn
        onDispose {
            currentView.keepScreenOn = false
        }
    }

    // 현재 시간 계기판만 1초마다 다시 그리도록, 시각 상태는 읽는 곳에서만 구독합니다.
    val clockTick = rememberClockTick(fixedClockMs)
    var cameraMode by rememberSaveable(defaultCameraMode) { mutableStateOf(defaultCameraMode) }
    var cameraPaused by remember { mutableStateOf(false) }
    var recenterKey by remember { mutableStateOf(0) }
    var topPx by remember { mutableStateOf(0) }
    var bottomPx by remember { mutableStateOf(0) }
    val haptics = LocalHapticFeedback.current
    var showAddMemoSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. 전체화면 MapLibre 벡터 지도 + 실시간 폴리라인
        GildamMapLibreView(
            trackPoints = liveState.liveTrackPoints,
            currentLocation = liveState.currentLocation,
            modifier = Modifier.fillMaxSize(),
            coursePoints = follow?.route ?: emptyList(),
            cameraMode = cameraMode,
            autoCameraPaused = cameraPaused,
            overlayTopPx = topPx,
            overlayBottomPx = bottomPx,
            speedMps = liveState.currentSpeedMps,
            distanceM = liveState.distanceMeters,
            bearingDeg = liveState.currentBearingDeg,
            onUserGesture = { cameraPaused = true },
            recenterKey = recenterKey
        )

        // 2. 상단 상태 바 & 네비게이션 칩 (Edge-to-Edge statusBars 인셋 준수)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .onSizeChanged { topPx = it.height }
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.gildam_logo),
                            contentDescription = "길담 로고",
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (liveState.isRecording) {
                                    if (liveState.isPaused) "⏸ 일시정지됨" else "🟢 기록 중 · 백그라운드 보호 활성"
                                } else {
                                    "길담 · 길이야기"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (keepScreenOn) {
                                    "화면을 켜 둡니다 · 1초 주기로 궤적 기록"
                                } else {
                                    "화면이 꺼져도 1초 주기로 궤적을 기록합니다"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (showTopNavIcons) Row {
                        IconButton(onClick = onNavigateToList) {
                            Icon(Icons.Outlined.FormatListBulleted, contentDescription = "기록 목록")
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = "설정")
                        }
                    }
                }
            }

            follow?.let { FollowCourseCard(it, onStopFollow, Modifier.padding(top = 8.dp)) }

            if (!liveState.isRecording && unfinishedRecording != null) {
                UnfinishedRecordingCard(
                    activity = unfinishedRecording,
                    onResume = onResumeUnfinishedRecording,
                    onFinishNow = onFinishUnfinishedRecording,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // GPS 신호 미약 경고 배너 (터널/실내)
            AnimatedVisibility(visible = liveState.isGpsSignalWeak) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = WarningAmber.copy(alpha = 0.95f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = "GPS 신호를 찾고 있어요. 마지막 확인 위치부터 이어집니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
            MapCameraControls(
                mode = cameraMode,
                paused = cameraPaused,
                onSelect = {
                    cameraMode = it
                    cameraPaused = false
                    recenterKey++
                    onSelectCameraMode(it)
                },
                onRecenter = { cameraPaused = false; recenterKey++ },
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        // 3. 지도 하단 묶음: 지도 위에 겹치는 계기판 1개 + 하단 대시보드
        //    칩을 카드 바로 위에 붙여 같은 묶음으로 읽히게 하고, 지도 위 버튼과는 겹치지 않게 합니다.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { bottomPx = it.height },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // '표시 안 함'을 고른 활동은 지도 위에 큰 값을 띄우지 않습니다.
            if (overlayMetric != GaugeMetric.NONE) {
                GaugeOverlayChip(
                    metric = overlayMetric,
                    live = liveState,
                    clockTick = clockTick,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            Card(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    // 이동 수단 태그 선택 (걷기 / 달리기 / 등산 / 자전거 / 자동차)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        TransportType.entries.forEach { type ->
                            val selected = (if (liveState.isRecording) liveState.transportType else selectedTransport) == type
                            TransportChip(type, selected, enabled = !liveState.isRecording) { onSelectTransport(type) }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 지도 아래 계기판: 지도 위 강조 지표를 뺀 나머지 (큰 값 2개 + 작은 값들, '표시 안 함'이면 5개 전부)
                    val heroMetrics = GaugeMetric.heroesFor(overlayMetric)
                    val subMetrics = GaugeMetric.subsFor(overlayMetric)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        heroMetrics.forEach { metric ->
                            StatSubCard(
                                metric = metric,
                                live = liveState,
                                clockTick = clockTick,
                                valueStyle = TabularStatHeroStyle.copy(fontSize = 38.sp, lineHeight = 44.sp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        subMetrics.forEach { metric ->
                            StatSubCard(
                                metric = metric,
                                live = liveState,
                                clockTick = clockTick,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 컨트롤 액션 버튼 영역
                    if (!liveState.isRecording) {
                        VividActionButton(
                            title = "길 기록 시작하기",
                            subtitle = if (follow != null) "코스를 따라 출발해요" else "${selectedTransport.labelKo} · 탭 한 번으로 출발!",
                            type = selectedTransport,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onStartRecording()
                            }
                        )
                    } else {
                        // 작은 아이콘 버튼 (길게 누르면 이름 말풍선) — 좁은 화면에서도 잘리지 않음
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TooltipIconButton(
                                icon = if (liveState.isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                                label = if (liveState.isPaused) "기록 재개" else "일시정지",
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (liveState.isPaused) onResumeRecording() else onPauseRecording()
                                },
                                style = IconButtonStyle.OUTLINED
                            )
                            TooltipIconButton(
                                icon = Icons.Outlined.AddLocationAlt,
                                label = "여기에 메모·사진 남기기",
                                onClick = { showAddMemoSheet = true },
                                style = IconButtonStyle.BRAND,
                                visualSize = 52.dp,
                                iconSize = 26.dp
                            )
                            TooltipIconButton(
                                icon = Icons.Outlined.Stop,
                                label = "기록 종료",
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onStopRecording()
                                },
                                style = IconButtonStyle.TONAL
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddMemoSheet) {
        AddWaypointBottomSheet(
            onDismiss = { showAddMemoSheet = false },
            onSaveWaypoint = { memo, caption, uri ->
                onAddWaypoint(memo, caption, uri)
                showAddMemoSheet = false
            }
        )
    }
}

private val UNFINISHED_TIME_FMT = DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREA)

@Composable
private fun UnfinishedRecordingCard(
    activity: ActivityEntity,
    onResume: () -> Unit,
    onFinishNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val startTimeStr = remember(activity.startedAtEpochMs) {
        UNFINISHED_TIME_FMT.format(
            Instant.ofEpochMilli(activity.startedAtEpochMs).atZone(ZoneId.systemDefault())
        )
    }
    val distanceKmStr = remember(activity.distanceMeters) {
        String.format(Locale.KOREA, "%.2f", activity.distanceMeters / 1000.0)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "끝나지 않은 기록이 있어요",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "「${activity.title}」 · $startTimeStr · 지금까지 $distanceKmStr km. 이어서 기록할까요?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                OutlinedButton(
                    onClick = onFinishNow,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("여기서 마치기", style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = onResume,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("이어서 기록", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun StatSubCard(
    metric: GaugeMetric,
    live: LiveRecordingState,
    clockTick: androidx.compose.runtime.State<Long>,
    modifier: Modifier = Modifier,
    valueStyle: androidx.compose.ui.text.TextStyle =
        TabularStatMediumStyle.copy(fontSize = 24.sp, lineHeight = 29.sp)
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = metric.labelWithUnit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            GaugeValueText(
                metric = metric,
                live = live,
                clockTick = clockTick,
                style = valueStyle.copy(textAlign = TextAlign.Center),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWaypointBottomSheet(
    onDismiss: () -> Unit,
    onSaveWaypoint: (memoText: String, caption: String?, photoUri: android.net.Uri?) -> Unit
) {
    var memoText by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "길 위의 메모·사진 남기기",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "사진 원본은 앱 폴더나 클라우드로 복사되지 않고 갤러리(MediaStore URI)로만 연결됩니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = memoText,
                onValueChange = { memoText = it },
                label = { Text("이곳에서 어떤 풍경이나 생각을 마주하셨나요?") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (selectedUri != null) "갤러리 사진 연결됨 (변경하려면 탭)"
                    else "갤러리에서 사진 연결하기 (로컬 전용)"
                )
            }

            if (selectedUri != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("사진 한 줄 캡션 (선택)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    onSaveWaypoint(
                        memoText.ifBlank { "길 위의 기록" },
                        caption.takeIf { it.isNotBlank() },
                        selectedUri
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("현재 위치에 기록 저장")
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * 계기판 값 문자열 (단위는 라벨에 따로 표시).
 * [clockNowMs] 는 현재 시간 계기판에만 쓰이고, 나머지 값은 [live] 에서 계산합니다.
 */
internal fun gaugeValue(metric: GaugeMetric, live: LiveRecordingState, clockNowMs: Long): String = when (metric) {
    GaugeMetric.DISTANCE -> GaugeFormat.distanceKm(live.distanceMeters)
    GaugeMetric.TOTAL_TIME -> GaugeFormat.durationHms(live.elapsedTotalMs)
    GaugeMetric.CURRENT_SPEED -> GaugeFormat.speedKmh(live.currentSpeedMps)
    GaugeMetric.MOVING_AVG_SPEED -> GaugeFormat.speedKmh(live.movingAvgSpeedMps)
    GaugeMetric.CLOCK -> GaugeFormat.clockHms(clockNowMs)
    GaugeMetric.NONE -> ""
}

/** 1초마다 현재 시각을 담는 상태. [fixed] 가 있으면 그 값으로 고정합니다(테스트·미리보기). */
@Composable
internal fun rememberClockTick(fixed: Long?): androidx.compose.runtime.State<Long> {
    if (fixed != null) return remember(fixed) { mutableStateOf(fixed) }
    val state = remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            state.value = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    return state
}

/** 계기판 값 텍스트. 현재 시간일 때만 시각 상태를 구독해서 화면 전체가 1초마다 다시 그려지지 않게 합니다. */
@Composable
internal fun GaugeValueText(
    metric: GaugeMetric,
    live: LiveRecordingState,
    clockTick: androidx.compose.runtime.State<Long>,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    maxLines: Int = Int.MAX_VALUE
) {
    val text = gaugeValue(metric, live, clockTick.value)
    Text(text = text, style = style, color = color, maxLines = maxLines)
}

/** 다른 화면(기록 목록·상세)에서도 같은 표기를 쓰도록 한 곳으로 모음 */
internal fun formatDurationHms(durationMs: Long): String = GaugeFormat.durationHms(durationMs)
