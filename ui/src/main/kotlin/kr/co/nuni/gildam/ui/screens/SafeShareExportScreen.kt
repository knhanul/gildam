package kr.co.nuni.gildam.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kr.co.nuni.gildam.data.gpx.GpxExportOptions
import kr.co.nuni.gildam.data.gpx.MaskedPreviewResult
import kr.co.nuni.gildam.data.local.entity.TrackPointEntity

/**
 * 공개·내보내기 정책 화면:
 * - 기본값은 비공개이며, GPX 공유 전 화이트리스트(허용 필드만)와 집 근처 마스킹 반경을 미리보기로 조정합니다.
 * - 사진 원본 파일은 GPX에 절대 포함되지 않습니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeShareExportScreen(
    trackPoints: List<TrackPointEntity>,
    computePreview: (List<TrackPointEntity>, Double) -> MaskedPreviewResult,
    onExportGpx: suspend (GpxExportOptions) -> String?,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var maskRadiusMeters by remember { mutableFloatStateOf(200f) }
    var includeWaypointsAndMemos by remember { mutableStateOf(false) }
    var includeCaptions by remember { mutableStateOf(false) }
    var includeTimestamps by remember { mutableStateOf(false) }
    var exportedXmlPreview by remember { mutableStateOf<String?>(null) }

    val preview = remember(trackPoints, maskRadiusMeters) {
        computePreview(trackPoints, maskRadiusMeters.toDouble())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("안전 공유 & GPX 내보내기") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🔒 화이트리스트 기반 GPX 내보내기",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "메모를 지워도 반복되는 출발·도착 좌표만으로 집 위치가 노출될 수 있습니다. 출발·도착 반경 마스킹과 포함할 항목을 직접 선택한 뒤 새로 만든 GPX만 내보냅니다. (사진 파일은 절대 포함되지 않습니다)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. 집 근처(출발/도착) 반경 마스킹 슬라이더
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "출발·도착지(집 근처) 마스킹 반경: ${maskRadiusMeters.toInt()} m",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Slider(
                        value = maskRadiusMeters,
                        onValueChange = { maskRadiusMeters = it },
                        valueRange = 0f..1000f,
                        steps = 9
                    )
                    Text(
                        text = "전체 ${preview.totalPointsCount}개 포인트 중 출발 ${preview.maskedStartCount}개 · 도착 ${preview.maskedEndCount}개 제외 → ${preview.visiblePoints.size}개 공개",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (preview.isOverMaskedWarning) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "⚠️ 마스킹 구간이 전체 경로의 80% 이상을 가립니다. 반경을 조금 줄여주세요.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. 공개 필드 화이트리스트 토글 옵션
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("포함할 선택 항목 (기본 전부 제외)", style = MaterialTheme.typography.titleMedium)

                    ToggleOptionRow(
                        title = "길 위의 메모 (<wpt><desc>) 포함",
                        subtitle = "체크 해제 시 궤적(<trkpt>)만 내보냅니다",
                        checked = includeWaypointsAndMemos,
                        onCheckedChange = { includeWaypointsAndMemos = it }
                    )

                    ToggleOptionRow(
                        title = "사진 캡션 (<wpt><name>) 포함",
                        subtitle = "사진 파일 자체는 어떤 경우에도 미포함됩니다",
                        checked = includeCaptions,
                        onCheckedChange = { includeCaptions = it }
                    )

                    ToggleOptionRow(
                        title = "포인트별 촬영·기록 시각 (<time>) 포함",
                        subtitle = "생활 시간대 루틴 노출을 막으려면 끄기를 권장합니다",
                        checked = includeTimestamps,
                        onCheckedChange = { includeTimestamps = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    scope.launch {
                        exportedXmlPreview = onExportGpx(
                            GpxExportOptions(
                                includeWaypointsAndMemos = includeWaypointsAndMemos,
                                includeCaptions = includeCaptions,
                                includeTimestamps = includeTimestamps,
                                privacyMaskRadiusMeters = maskRadiusMeters.toDouble()
                            )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("화이트리스트 GPX 생성 및 미리보기")
            }

            exportedXmlPreview?.let { xml ->
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("생성된 GPX 1.1 미리보기", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = xml.take(900) + if (xml.length > 900) "\n... (생략)" else "",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleOptionRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
