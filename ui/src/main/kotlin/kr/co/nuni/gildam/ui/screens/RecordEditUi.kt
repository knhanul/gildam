package kr.co.nuni.gildam.ui.screens

import kr.co.nuni.gildam.ui.l10n.*
import androidx.compose.ui.res.stringResource
import kr.co.nuni.gildam.ui.R
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.model.SyncState
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.data.repository.RecordEditPolicy
import kr.co.nuni.gildam.ui.illustration.IconButtonStyle
import kr.co.nuni.gildam.ui.illustration.TooltipIconButton

/** 화면에 보이는 클라우드 상태 */
enum class CloudStatus(val labelRes: Int) {
    NOT_UPLOADED(R.string.upload_not_uploaded),
    UPLOADING(R.string.upload_uploading),
    DONE(R.string.upload_done),
    FAILED(R.string.upload_failed),
    DELETING(R.string.upload_deleting);
    val label: String @Composable get() = stringResource(labelRes)


    companion object {
        fun of(s: SyncState): CloudStatus = when (s) {
            SyncState.LOCAL_ONLY -> NOT_UPLOADED
            SyncState.PENDING_UPLOAD, SyncState.SYNCING -> UPLOADING
            SyncState.SYNCED -> DONE
            SyncState.SYNC_ERROR -> FAILED
            SyncState.PENDING_DELETE -> DELETING
        }
    }
}

@Composable
fun CloudStatusChip(status: CloudStatus, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val (icon: ImageVector, bg: Color, fg: Color) = when (status) {
        CloudStatus.NOT_UPLOADED -> Triple(Icons.Outlined.PhoneAndroid, cs.surface, cs.onSurfaceVariant)
        CloudStatus.UPLOADING, CloudStatus.DELETING -> Triple(Icons.Outlined.CloudSync, cs.secondaryContainer, cs.onSecondaryContainer)
        CloudStatus.DONE -> Triple(Icons.Outlined.CloudDone, cs.primaryContainer, cs.onPrimaryContainer)
        CloudStatus.FAILED -> Triple(Icons.Outlined.ErrorOutline, cs.errorContainer, cs.onErrorContainer)
    }
    Surface(shape = RoundedCornerShape(50), color = bg, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(status.label, style = MaterialTheme.typography.labelSmall, color = fg)
        }
    }
}

/** 기록 카드·상세에 쓰는 작은 아이콘 버튼 줄 (길게 누르면 말풍선) */
@Composable
fun RecordActionButtons(
    activity: ActivityEntity,
    isLoggedIn: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUpload: () -> Unit,
    small: Boolean = true
) {
    val vs = if (small) 34.dp else 40.dp
    val ic = if (small) 18.dp else 20.dp
    Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isLoggedIn && RecordEditPolicy.canRequestUpload(activity)) {
            val failed = activity.syncState == SyncState.SYNC_ERROR
            TooltipIconButton(
                icon = Icons.Outlined.CloudUpload,
                label = if (failed) stringResource(R.string.upload_retry) else stringResource(R.string.upload_now),
                onClick = onUpload, style = IconButtonStyle.BRAND, visualSize = vs, iconSize = ic
            )
        }
        TooltipIconButton(icon = Icons.Outlined.Edit, label = stringResource(R.string.edit_title_activity), onClick = onEdit, style = IconButtonStyle.TONAL, visualSize = vs, iconSize = ic)
        TooltipIconButton(icon = Icons.Outlined.Delete, label = stringResource(R.string.record_delete), onClick = onDelete, style = IconButtonStyle.OUTLINED, visualSize = vs, iconSize = ic)
    }
}

/** 수정 시트 내용 (미리보기·Paparazzi 에서도 그대로 씀) */
@Composable
fun RecordEditForm(
    initialTitle: String,
    initialType: TransportType,
    onSave: (String, TransportType) -> Unit,
    onCancel: () -> Unit,
    willSyncToServer: Boolean
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var type by remember(initialType) { mutableStateOf(initialType) }
    val valid = RecordEditPolicy.cleanTitle(title) != null
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text(stringResource(R.string.edit_record_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { if (it.length <= RecordEditPolicy.MAX_TITLE) title = it },
            label = { Text(stringResource(R.string.edit_title_label)) },
            singleLine = true,
            isError = !valid,
            supportingText = { if (!valid) Text(stringResource(R.string.edit_title_required)) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.edit_activity_label), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            TransportType.entries.forEach { t ->
                FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (willSyncToServer) Icons.Outlined.CloudSync else Icons.Outlined.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(
                if (willSyncToServer) stringResource(R.string.edit_sync_note) else stringResource(R.string.edit_local_note),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onSave(title, type) }, enabled = valid) { Text(stringResource(R.string.action_save)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordEditSheet(activity: ActivityEntity, onSave: (String, TransportType) -> Unit, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        RecordEditForm(
            initialTitle = activity.title, initialType = activity.transportType,
            onSave = { t, ty -> onSave(t, ty); onDismiss() }, onCancel = onDismiss,
            willSyncToServer = activity.syncState != SyncState.LOCAL_ONLY
        )
    }
}

@Composable
fun DeleteRecordContent(activity: ActivityEntity, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val onServer = activity.syncState != SyncState.LOCAL_ONLY
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
        Column(Modifier.padding(24.dp)) {
            Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.delete_confirm_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(10.dp))
            Text("‘${activity.title}’", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                if (onServer) stringResource(R.string.delete_confirm_server) else stringResource(R.string.delete_confirm_local),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(stringResource(R.string.delete_keeps_gallery), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteRecordDialog(activity: ActivityEntity, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        DeleteRecordContent(activity, onConfirm = { onConfirm(); onDismiss() }, onCancel = onDismiss)
    }
}

/** 화면 상태: 어떤 기록의 시트/대화상자가 열려 있는지 */
@Composable
fun RecordEditHost(
    editing: ActivityEntity?,
    deleting: ActivityEntity?,
    onEdit: (String, String, TransportType) -> Unit,
    onDelete: (String) -> Unit,
    onDismissEdit: () -> Unit,
    onDismissDelete: () -> Unit
) {
    editing?.let { a -> RecordEditSheet(a, onSave = { t, ty -> onEdit(a.id, t, ty) }, onDismiss = onDismissEdit) }
    deleting?.let { a -> DeleteRecordDialog(a, onConfirm = { onDelete(a.id) }, onDismiss = onDismissDelete) }
}
