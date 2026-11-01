package kr.co.nuni.gildam.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.merge.RecordMerger
import kr.co.nuni.gildam.data.repository.RecordEditPolicy

/**
 * 고른 기록을 하나로 합치기 전에 보여 주는 확인 창.
 * 합치면 원본은 사라지고 새 기록 하나가 남는다는 점과, 합쳐질 때의 합계를 같이 알려 줍니다.
 */
@Composable
fun MergeRecordsContent(
    sources: List<ActivityEntity>,
    defaultTitle: String,
    merging: Boolean,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember(defaultTitle) { mutableStateOf(defaultTitle) }
    val sorted = remember(sources) { sources.sortedBy { it.startedAtEpochMs } }
    val totalDistanceM = sorted.sumOf { it.distanceMeters }
    val totalDurationMs = sorted.sumOf { it.totalDurationMs }
    val anyOnServer = sorted.any { it.syncState != kr.co.nuni.gildam.data.model.SyncState.LOCAL_ONLY }
    val valid = RecordEditPolicy.cleanTitle(title) != null && sources.size in RecordMerger.MIN_COUNT..RecordMerger.MAX_COUNT

    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.CallMerge, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("${sorted.size}개 기록을 하나로 합칠까요?", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                sorted.forEachIndexed { i, a ->
                    Text(
                        "${i + 1}. ${a.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("합계", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "%.2f km · %s".format(totalDistanceM / 1000.0, formatDurationHms(totalDurationMs)),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= RecordEditPolicy.MAX_TITLE) title = it },
                label = { Text("합친 기록 제목") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (anyOnServer) "합친 기록은 새 기록으로 만들어져요. 원본은 기기와 클라우드에서 지워지고, 사진 파일은 그대로 남아요."
                else "합친 기록은 이 기기에 새로 만들어져요. 원본 기록은 지워지고, 사진 파일은 그대로 남아요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel, enabled = !merging) { Text("취소") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onConfirm(title) }, enabled = valid && !merging) {
                    if (merging) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("합치는 중")
                    } else {
                        Text("하나로 합치기")
                    }
                }
            }
        }
    }
}
