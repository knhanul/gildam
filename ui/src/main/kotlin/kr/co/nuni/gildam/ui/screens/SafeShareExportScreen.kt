package kr.co.nuni.gildam.ui.screens

import androidx.compose.ui.res.stringResource
import kr.co.nuni.gildam.ui.R
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kr.co.nuni.gildam.data.gpx.GpxExportOptions
import kr.co.nuni.gildam.data.gpx.GpxExportResult
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
    onExportGpx: suspend (GpxExportOptions) -> GpxExportResult?,
    onNavigateBack: () -> Unit,
    /** 경로 점을 다 불러왔는지 (불러오기 전엔 '점 없음' 안내를 띄우지 않음) */
    pointsLoaded: Boolean = true
) {
    // 경로 점이 하나도 없는 기록은 빈 GPX 가 되므로 내보내기를 막고 이유를 보여 줌
    val noPoints = pointsLoaded && trackPoints.isEmpty()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var maskRadiusMeters by remember { mutableFloatStateOf(200f) }
    var includeWaypointsAndMemos by remember { mutableStateOf(false) }
    var includeCaptions by remember { mutableStateOf(false) }
    var includeTimestamps by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<GpxExportResult?>(null) }

    // SAF: 사용자가 저장 위치/파일명을 고르면 GPX XML을 해당 Uri에 기록
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri ->
        val payload = pendingExport
        pendingExport = null
        if (uri != null && payload != null) {
            val saved = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(payload.xml.toByteArray(Charsets.UTF_8))
                } ?: error("output stream is null")
            }.isSuccess
            Toast.makeText(
                context,
                context.getString(if (saved) R.string.gpx_saved else R.string.gpx_save_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val preview = remember(trackPoints, maskRadiusMeters) {
        computePreview(trackPoints, maskRadiusMeters.toDouble())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.share_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                        text = stringResource(R.string.share_whitelist_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.share_whitelist_desc),
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
                        text = stringResource(R.string.share_mask_radius, maskRadiusMeters.toInt()),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Slider(
                        value = maskRadiusMeters,
                        onValueChange = { maskRadiusMeters = it },
                        valueRange = 0f..1000f,
                        steps = 9
                    )
                    Text(
                        text = stringResource(R.string.share_mask_preview, preview.totalPointsCount, preview.maskedStartCount, preview.maskedEndCount, preview.visiblePoints.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (preview.isOverMaskedWarning) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.share_mask_warning),
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
                    Text(stringResource(R.string.share_optional_items), style = MaterialTheme.typography.titleMedium)

                    ToggleOptionRow(
                        title = stringResource(R.string.share_include_notes),
                        subtitle = stringResource(R.string.share_include_notes_desc),
                        checked = includeWaypointsAndMemos,
                        onCheckedChange = { includeWaypointsAndMemos = it }
                    )

                    ToggleOptionRow(
                        title = stringResource(R.string.share_include_captions),
                        subtitle = stringResource(R.string.share_include_captions_desc),
                        checked = includeCaptions,
                        onCheckedChange = { includeCaptions = it }
                    )

                    ToggleOptionRow(
                        title = stringResource(R.string.share_include_times),
                        subtitle = stringResource(R.string.share_include_times_desc),
                        checked = includeTimestamps,
                        onCheckedChange = { includeTimestamps = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    scope.launch {
                        exporting = true
                        val payload = onExportGpx(
                            GpxExportOptions(
                                includeWaypointsAndMemos = includeWaypointsAndMemos,
                                includeCaptions = includeCaptions,
                                includeTimestamps = includeTimestamps,
                                privacyMaskRadiusMeters = maskRadiusMeters.toDouble()
                            )
                        )
                        exporting = false
                        if (payload != null) {
                            pendingExport = payload
                            saveLauncher.launch(payload.suggestedFileName)
                        } else {
                            Toast.makeText(context, context.getString(R.string.share_record_not_found), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !exporting && !noPoints && pointsLoaded,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (exporting) stringResource(R.string.gpx_exporting) else stringResource(R.string.gpx_export_save))
            }
            if (noPoints) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.gpx_export_no_points),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
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
