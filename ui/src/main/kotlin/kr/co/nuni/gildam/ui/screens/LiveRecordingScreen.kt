package kr.co.nuni.gildam.ui.screens

import kr.co.nuni.gildam.ui.l10n.*
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.platform.LocalConfiguration
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
import kotlin.math.roundToInt
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
    /** 코스 따라가기 중이면 코스 선·남은 거리 표시 */
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
    fixedClockMs: Long? = null,
    /** 기록 점검 결과. null 이면 휴대폰 상태를 직접 점검 (테스트에서는 목록을 넘김) */
    healthIssues: List<RecordingIssue>? = null,
    /** 출발 전 날씨. 기록 중에는 쓰지 않습니다. */
    startWeather: StartWeatherUi = StartWeatherUi.Hidden
) {
    val checkedIssues by rememberRecordingHealth()
    val issues = remember(healthIssues, checkedIssues, liveState.isGpsSignalWeak, liveState.isRecording) {
        val base = healthIssues ?: checkedIssues
        val withGps = if (liveState.isRecording && liveState.isGpsSignalWeak && RecordingIssue.GPS_WEAK !in base) base + RecordingIssue.GPS_WEAK else base
        withGps.sortedBy { it.ordinal }
    }
    var showIssues by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
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
    // 따라가기 중에도 나침반은 보여 줍니다. 북쪽 위는 쓰지 않고, 누르면 진행 방향 위로 다시 맞춥니다.
    val followingCourse = follow != null
    var cameraMode by rememberSaveable {
        mutableStateOf(defaultCameraMode)
    }
    // 코스 따라가기 중에는 진행 방향 위만 (두 손가락 회전은 그대로, 나침반으로 복귀)
    var lastFollowMode by rememberSaveable {
        mutableStateOf(if (defaultCameraMode.isFollow) defaultCameraMode else MapCameraMode.HEADING)
    }
    val effectiveMode = if (followingCourse && cameraMode == MapCameraMode.FOLLOW) MapCameraMode.HEADING else cameraMode
    var cameraPaused by remember { mutableStateOf(false) }
    // 실제 지도 회전각 (나침반 바늘이 진짜 북쪽을 가리키게)
    var mapBearing by remember { mutableStateOf(0f) }
    var recenterKey by remember { mutableStateOf(0) }
    var topPx by remember { mutableStateOf(0) }
    var bottomPx by remember { mutableStateOf(0) }
    val haptics = LocalHapticFeedback.current
    var showAddMemoSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. 전체화면 MapLibre 벡터 지도 + 실시간 폴리라인
        // 종료 직후에는 방금 기록한 경로·위치 스냅샷을 유지 (지도가 서울로 튀지 않게)
        val displayTrack = if (liveState.isRecording) liveState.liveTrackPoints else liveState.lastFinishedTrackPoints
        val displayLocation = if (liveState.isRecording) liveState.currentLocation else liveState.lastFinishedLocation
        GildamMapLibreView(
            trackPoints = displayTrack,
            currentLocation = displayLocation,
            modifier = Modifier.fillMaxSize(),
            coursePoints = follow?.route ?: emptyList(),
            cameraMode = effectiveMode,
            autoCameraPaused = cameraPaused,
            overlayTopPx = topPx,
            overlayBottomPx = bottomPx,
            speedMps = liveState.currentSpeedMps,
            distanceM = liveState.distanceMeters,
            bearingDeg = liveState.currentBearingDeg,
            onUserGesture = { cameraPaused = true },
            recenterKey = recenterKey,
            routeOpacity = if (liveState.isRecording) 1.0f else 0.6f,
            // 기록·일시정지 중에는 현재 위치를 진행 방향 화살표로. 끝난 뒤에는 출발·도착 점만
            showDirectionArrow = liveState.isRecording,
            offCourse = follow?.isOffCourse == true,
            followingCourse = followingCourse,
            onMapBearingChanged = { mapBearing = it }
        )

        // 2. 상단 상태 바 & 네비게이션 칩 (Edge-to-Edge statusBars 인셋 준수)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .onSizeChanged { topPx = it.height }
        ) {
            // 기록 중에는 상단 안내 띠를 없애고, 작은 상태 점 + 지도 버튼 한 줄만 둡니다.
            if (!liveState.isRecording) Surface(
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
                            contentDescription = stringResource(R.string.logo_cd),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (liveState.isRecording) {
                                    if (liveState.isPaused) stringResource(R.string.live_status_paused) else stringResource(R.string.live_status_recording)
                                } else {
                                    stringResource(R.string.live_brand)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (keepScreenOn) {
                                    stringResource(R.string.live_screen_on_sub)
                                } else {
                                    stringResource(R.string.live_screen_off_sub)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (showTopNavIcons) Row {
                        IconButton(onClick = onNavigateToList) {
                            Icon(Icons.Outlined.FormatListBulleted, contentDescription = stringResource(R.string.records_list_cd))
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
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
            AnimatedVisibility(visible = liveState.isGpsSignalWeak && !liveState.isRecording) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = WarningAmber.copy(alpha = 0.95f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.gps_searching_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
            MapCameraControls(
                mode = effectiveMode,
                paused = cameraPaused,
                onSelect = {
                    cameraMode = it
                    if (it.isFollow) lastFollowMode = it
                    cameraPaused = false
                    recenterKey++
                    onSelectCameraMode(it)
                },
                onRecenter = { cameraPaused = false; recenterKey++ },
                allowNorthUp = !followingCourse,
                showCompass = true,
                mapBearingDeg = mapBearing,
                status = if (liveState.isRecording) RecordingStatus.of(liveState.isPaused, false) else null,
                // 기록 시작 전에도 문제를 미리 보여 줌 (시작은 막지 않음)
                issues = issues,
                onOpenIssues = { showIssues = true },
                followMode = lastFollowMode,
                // 따라가기 띠가 있을 때는 버튼 행·상태 칩이 바로 아래에 붙도록 작은 간격만 둡니다.
                modifier = Modifier.padding(
                    top = when {
                        follow != null && liveState.isRecording -> 8.dp
                        liveState.isRecording -> 0.dp
                        else -> 10.dp
                    }
                )
            )
        }

        // 3. 지도 하단 묶음: 지도 위에 겹치는 계기판 1개 + 하단 대시보드
        //    칩을 카드 바로 위에 붙여 같은 묶음으로 읽히게 하고, 지도 위 버튼과는 겹치지 않게 합니다.
        Column(
            // 지도 위 숫자까지 포함한 높이를 카메라 여백으로 써서, 현재 위치가 숫자·계기판에 가리지 않게 합니다.
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { bottomPx = it.height },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val screenHeightDp = LocalConfiguration.current.screenHeightDp
            // 작은 화면(높이 640dp 수준)에서 기록 중 계기판이 2×3 6칸일 때 지도가 너무 줄어들지 않도록 여백·크기를 살짝 줄임
            val compactGauge = liveState.isRecording && screenHeightDp <= 680
            val cardPadV = if (compactGauge) 11.dp else 16.dp
            val rowGap = if (compactGauge) 7.dp else 10.dp
            val cellGap = if (compactGauge) 8.dp else 12.dp
            val actionSpacer = if (compactGauge) 10.dp else 14.dp
            val gaugeFontSp = if (compactGauge) GAUGE_VALUE_COMPACT_SP else GAUGE_VALUE_SP
            val gaugeValueStyle = TabularStatHeroStyle.copy(
                fontSize = gaugeFontSp.sp,
                lineHeight = (gaugeFontSp + 5).sp
            )

            // '표시 안 함'을 고른 활동은 지도 위에 큰 값을 띄우지 않습니다.
            if (overlayMetric != GaugeMetric.NONE) {
                GaugeOverlayChip(
                    metric = overlayMetric,
                    live = liveState,
                    clockTick = clockTick,
                    modifier = Modifier.padding(bottom = if (compactGauge) 2.dp else 6.dp)
                )
            }

            Card(
                shape = RoundedCornerShape(topStart = if (compactGauge) 24.dp else 28.dp, topEnd = if (compactGauge) 24.dp else 28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                // 지도 위 칩은 지도 위에 떠 있으므로 카드 높이만 아래 여백으로 잡음
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = if (compactGauge) 16.dp else 20.dp, vertical = cardPadV)
                ) {
                    // 이동 수단 태그 선택 (걷기 / 달리기 / 등산 / 자전거 / 자동차)
                    // 기록 중에는 지도를 크게 보려고 활동 아이콘을 숨깁니다.
                    if (!liveState.isRecording) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        ) {
                            TransportType.entries.forEach { type ->
                                TransportChip(type, selected = selectedTransport == type, enabled = true) { onSelectTransport(type) }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        StartWeatherSection(state = startWeather, transportType = selectedTransport)
                    }

                    // 지도 아래 계기판: 지도 위 강조 지표를 뺀 나머지 (큰 값 2개 + 작은 값들, '표시 안 함'이면 5개 전부)
                    val heroMetrics = GaugeMetric.heroesFor(overlayMetric)
                    val subMetrics = GaugeMetric.subsFor(overlayMetric)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(cellGap)
                    ) {
                        heroMetrics.forEach { metric ->
                            StatSubCard(
                                metric = metric,
                                live = liveState,
                                clockTick = clockTick,
                                valueStyle = gaugeValueStyle,
                                compactVertical = compactGauge,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(rowGap))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(cellGap)
                    ) {
                        subMetrics.forEach { metric ->
                            StatSubCard(
                                metric = metric,
                                live = liveState,
                                clockTick = clockTick,
                                valueStyle = gaugeValueStyle,
                                compactVertical = compactGauge,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 기록 중에는 3번째 줄(2×3, 6칸)로 오르막·내리막 + 실시간 날씨 칸을 더합니다.
                    if (liveState.isRecording) {
                        Spacer(modifier = Modifier.height(rowGap))
                        val activeTransport = liveState.transportType
                        val staleWeather = liveState.liveWeather.isStale(clockTick.value)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(cellGap)
                        ) {
                            ElevationGainLossGaugeCard(
                                gainM = liveState.elevationGainM,
                                lossM = liveState.elevationLossM,
                                valueStyle = gaugeValueStyle,
                                compactVertical = compactGauge,
                                modifier = Modifier.weight(1f)
                            )
                            LiveWeatherGaugeCard(
                                weather = liveState.liveWeather.weather,
                                isStale = staleWeather,
                                transportType = activeTransport,
                                valueStyle = gaugeValueStyle,
                                compactVertical = compactGauge,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(actionSpacer))

                    // 컨트롤 액션 버튼 영역
                    if (!liveState.isRecording) {
                        VividActionButton(
                            title = stringResource(R.string.live_start_title),
                            subtitle = if (follow != null) stringResource(R.string.live_start_follow_sub) else stringResource(R.string.live_start_sub, selectedTransport.label),
                            type = selectedTransport,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onStartRecording()
                            }
                        )
                    } else {
                        // 작은 아이콘 버튼 (길게 누르면 이름 말풍선) — 좁은 화면에서도 잘리지 않음
                        val btnMainSize = if (compactGauge) 46.dp else 52.dp
                        val btnSubSize = if (compactGauge) 40.dp else 44.dp
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TooltipIconButton(
                                icon = if (liveState.isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                                label = if (liveState.isPaused) stringResource(R.string.live_resume) else stringResource(R.string.live_pause),
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (liveState.isPaused) onResumeRecording() else onPauseRecording()
                                },
                                style = IconButtonStyle.OUTLINED,
                                visualSize = btnSubSize
                            )
                            TooltipIconButton(
                                icon = Icons.Outlined.AddLocationAlt,
                                label = stringResource(R.string.live_add_note),
                                onClick = { showAddMemoSheet = true },
                                style = IconButtonStyle.BRAND,
                                visualSize = btnMainSize,
                                iconSize = if (compactGauge) 24.dp else 26.dp
                            )
                            TooltipIconButton(
                                icon = Icons.Outlined.Stop,
                                label = stringResource(R.string.live_stop),
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onStopRecording()
                                },
                                style = IconButtonStyle.TONAL,
                                visualSize = btnSubSize
                            )
                        }
                    }
                }
            }
        }
    }

    if (showIssues && issues.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { showIssues = false }) {
            RecordingIssuesDetail(issues, onOpenSettings = { openIssueSettings(it, context) })
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


@Composable
private fun UnfinishedRecordingCard(
    activity: ActivityEntity,
    onResume: () -> Unit,
    onFinishNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = currentLocale()
    val startTimeStr = remember(activity.startedAtEpochMs, locale) {
        shortDateTimeFormatter(locale).format(
            Instant.ofEpochMilli(activity.startedAtEpochMs).atZone(ZoneId.systemDefault())
        )
    }
    val distanceKmStr = remember(activity.distanceMeters) {
        String.format(Locale.ROOT, "%.2f", activity.distanceMeters / 1000.0)
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
                text = stringResource(R.string.unfinished_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.unfinished_desc, activity.title, startTimeStr, distanceKmStr),
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
                    Text(stringResource(R.string.unfinished_finish), style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = onResume,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(stringResource(R.string.unfinished_continue), style = MaterialTheme.typography.labelMedium)
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
    compactVertical: Boolean = false,
    valueStyle: androidx.compose.ui.text.TextStyle =
        // 아래 계기판 숫자는 모두 같은 크기. 넘치면 한 줄에 맞게 줄임.
        TabularStatHeroStyle.copy(fontSize = GAUGE_VALUE_SP.sp, lineHeight = (GAUGE_VALUE_SP + 6).sp)
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = if (compactVertical) 6.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = metric.labelWithUnitText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(if (compactVertical) 1.dp else 2.dp))
            GaugeValueText(
                metric = metric,
                live = live,
                clockTick = clockTick,
                style = valueStyle.copy(textAlign = TextAlign.Center),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                autoShrink = true
            )
        }
    }
}

/**
 * 기록 중 계기판 3번째 줄 왼쪽 칸: 오르막·내리막 (`↑ 125 m  ↓ 98 m`, 정수 m).
 * 숫자 크기는 다른 계기판 칸과 같게 시작하며, 칸 폭을 넘기면 자동으로 한 줄에 맞춰 줄입니다.
 */
@Composable
private fun ElevationGainLossGaugeCard(
    gainM: Float,
    lossM: Float,
    valueStyle: androidx.compose.ui.text.TextStyle,
    compactVertical: Boolean,
    modifier: Modifier = Modifier
) {
    val text = stringResource(
        R.string.gauge_elevation_pair,
        gainM.coerceAtLeast(0f).roundToInt(),
        lossM.coerceAtLeast(0f).roundToInt()
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = if (compactVertical) 6.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.gauge_elevation_gain_loss),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(if (compactVertical) 1.dp else 2.dp))
            AutoShrinkSingleLineText(
                text = text,
                style = valueStyle.copy(textAlign = TextAlign.Center),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
internal fun AutoShrinkSingleLineText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    minScale: Float = 0.55f
) {
    var scale by remember(text.length) { mutableStateOf(1f) }
    Text(
        text = text,
        style = style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale),
        color = color,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { r -> if (r.hasVisualOverflow && scale > minScale) scale -= 0.05f }
    )
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
                text = stringResource(R.string.note_sheet_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.note_sheet_desc),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = memoText,
                onValueChange = { memoText = it },
                label = { Text(stringResource(R.string.note_hint)) },
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
                    if (selectedUri != null) stringResource(R.string.note_photo_linked)
                    else stringResource(R.string.note_photo_link)
                )
            }

            if (selectedUri != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text(stringResource(R.string.note_caption)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            val defaultNoteTitle = stringResource(R.string.note_default_title)
            Button(
                onClick = {
                    onSaveWaypoint(
                        memoText.ifBlank { defaultNoteTitle },
                        caption.takeIf { it.isNotBlank() },
                        selectedUri
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(stringResource(R.string.note_save))
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
    maxLines: Int = Int.MAX_VALUE,
    /** 한 줄에 안 들어가면 글자 크기를 조금씩 줄여 맞춤 */
    autoShrink: Boolean = false
) {
    val text = gaugeValue(metric, live, clockTick.value)
    if (!autoShrink) {
        Text(text = text, style = style, color = color, maxLines = maxLines)
        return
    }
    var scale by remember(text.length) { mutableStateOf(1f) }
    Text(
        text = text,
        style = style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale),
        color = color,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { r -> if (r.hasVisualOverflow && scale > 0.6f) scale -= 0.05f }
    )
}

/** 아래 계기판 숫자 크기(sp) */
internal const val GAUGE_VALUE_SP = 28
internal const val GAUGE_VALUE_COMPACT_SP = 23

/** 다른 화면(기록 목록·상세)에서도 같은 표기를 쓰도록 한 곳으로 모음 */
internal fun formatDurationHms(durationMs: Long): String = GaugeFormat.durationHms(durationMs)
