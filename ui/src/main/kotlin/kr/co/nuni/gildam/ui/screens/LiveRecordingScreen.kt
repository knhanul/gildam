package kr.co.nuni.gildam.ui.screens

import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.map.GildamMapLibreView
import kr.co.nuni.gildam.recording.LiveRecordingState
import kr.co.nuni.gildam.ui.theme.TabularStatHeroStyle
import kr.co.nuni.gildam.ui.theme.TabularStatMediumStyle
import kr.co.nuni.gildam.ui.theme.WarningAmber

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
    onStopFollow: () -> Unit = {}
) {
    val haptics = LocalHapticFeedback.current
    var showAddMemoSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. 전체화면 MapLibre 벡터 지도 + 실시간 폴리라인
        GildamMapLibreView(
            trackPoints = liveState.liveTrackPoints,
            currentLocation = liveState.currentLocation,
            modifier = Modifier.fillMaxSize(),
            coursePoints = follow?.route ?: emptyList()
        )

        // 2. 상단 상태 바 & 네비게이션 칩 (Edge-to-Edge statusBars 인셋 준수)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 8.dp)
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
                            text = "화면이 꺼져도 1초 주기로 궤적을 기록합니다",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
        }

        // 3. 하단 플로팅 에디토리얼 대시보드 (Tabular Figures 고정폭 숫자 적용)
        Card(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                // 이동 수단 태그 선택 (도보 / 자전거 / 자동차)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TransportType.entries.forEach { type ->
                        val selected = (if (liveState.isRecording) liveState.transportType else selectedTransport) == type
                        FilterChip(
                            selected = selected,
                            onClick = { if (!liveState.isRecording) onSelectTransport(type) },
                            label = { Text(type.labelKo) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 메인 히어로 통계: 누적 거리(km) & 총 시간 (Tabular Figures)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "누적 거리 (km)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format("%.2f", liveState.distanceMeters / 1000.0),
                            style = TabularStatHeroStyle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "총 시간",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDurationHms(liveState.elapsedTotalMs),
                            style = TabularStatHeroStyle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 보조 속도 카드: 현재 속도 & 이동 평균 속도
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatSubCard(
                        label = "현재 속도",
                        value = String.format("%.1f km/h", liveState.currentSpeedMps * 3.6f),
                        modifier = Modifier.weight(1f)
                    )
                    StatSubCard(
                        label = "이동 평균 속도",
                        value = String.format("%.1f km/h", liveState.movingAvgSpeedMps * 3.6f),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 컨트롤 액션 버튼 영역
                if (!liveState.isRecording) {
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStartRecording()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "길 기록 시작하기",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (liveState.isPaused) onResumeRecording() else onPauseRecording()
                            },
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = if (liveState.isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (liveState.isPaused) "재개" else "일시정지")
                        }

                        Button(
                            onClick = { showAddMemoSheet = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "여기에 메모·사진",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onStopRecording()
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Outlined.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("종료")
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

@Composable
private fun StatSubCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = TabularStatMediumStyle,
                color = MaterialTheme.colorScheme.onSurface
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

internal fun formatDurationHms(durationMs: Long): String {
    val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}
