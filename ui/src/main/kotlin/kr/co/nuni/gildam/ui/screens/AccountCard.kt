package kr.co.nuni.gildam.ui.screens

import androidx.compose.ui.res.pluralStringResource
import kr.co.nuni.gildam.ui.l10n.*
import androidx.compose.ui.res.stringResource
import kr.co.nuni.gildam.ui.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.nuni.gildam.auth.NuniSessionState
import kr.co.nuni.gildam.data.prefs.ProfilePrefs
import kr.co.nuni.gildam.ui.illustration.GildamTooltip
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kr.co.nuni.gildam.sync.SyncUiStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 설정 첫 화면 최상단 계정·동기화 카드.
 * 사용자에게는 '카카오 로그인'으로 보이고, 실제 계정·토큰은 NUNI ID(idp_hint=kakao)가 발급합니다.
 */
@Composable
internal fun AccountCard(
    sessionState: NuniSessionState,
    syncStatus: SyncUiStatus,
    lastSyncSuccessAtMs: Long,
    serverTrackCount: Int?,
    nickname: String? = null,
    onUpdateNickname: (String) -> Boolean = { true },
    onKakaoLogin: () -> Unit,
    onSwitchAccount: () -> Unit,
    onManualSync: () -> Unit,
    onLogout: () -> Unit,
    onOpenPhotoStorage: () -> Unit,
    /** 디버그 빌드에서만 전달 (릴리스는 null → 버튼 숨김) */
    onDemoSimulateLogin: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.account_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (sessionState.isLoggedIn) {
                LoggedInContent(
                    sessionState, syncStatus, lastSyncSuccessAtMs, serverTrackCount,
                    nickname, onUpdateNickname,
                    onKakaoLogin, onSwitchAccount, onManualSync, onLogout
                )
            } else {
                Text(
                    text = stringResource(R.string.account_signin_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                KakaoLoginButton(onClick = onKakaoLogin)
                NuniFootnote()
                if (onDemoSimulateLogin != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onDemoSimulateLogin,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.fillMaxWidth().height(34.dp)
                    ) {
                        Text(stringResource(R.string.account_debug_demo), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            PhotoStorageFootnote(onOpenPhotoStorage)
        }
    }
}

@Composable
private fun LoggedInContent(
    sessionState: NuniSessionState,
    syncStatus: SyncUiStatus,
    lastSyncSuccessAtMs: Long,
    serverTrackCount: Int?,
    nickname: String?,
    onUpdateNickname: (String) -> Boolean,
    onKakaoLogin: () -> Unit,
    onSwitchAccount: () -> Unit,
    onManualSync: () -> Unit,
    onLogout: () -> Unit
) {
    var showNicknameDialog by remember { mutableStateOf(false) }
    Text(
        text = stringResource(R.string.account_signed_in_kakao),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = nickname ?: stringResource(R.string.account_nickname_empty),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (nickname != null) FontWeight.SemiBold else FontWeight.Normal,
            color = if (nickname != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        GildamTooltip(label = stringResource(R.string.account_nickname_change)) {
            IconButton(onClick = { showNicknameDialog = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.account_nickname_change),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
    if (showNicknameDialog) {
        NicknameDialog(
            initial = nickname.orEmpty(),
            onDismiss = { showNicknameDialog = false },
            onSave = { if (onUpdateNickname(it)) showNicknameDialog = false }
        )
    }
    if (serverTrackCount != null) {
        Text(
            text = pluralStringResource(R.plurals.account_server_tracks, serverTrackCount, serverTrackCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(modifier = Modifier.height(6.dp))

    if (sessionState.requiresReauthBanner) {
        SyncStatusRow(
            icon = Icons.Outlined.ErrorOutline,
            text = stringResource(R.string.account_session_expired),
            emphasized = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        KakaoLoginButton(onClick = onKakaoLogin)
        TextButton(
            onClick = onLogout,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.height(30.dp)
        ) { Text(stringResource(R.string.account_sign_out), style = MaterialTheme.typography.labelSmall) }
    } else {
        when (syncStatus) {
            SyncUiStatus.SYNCING -> SyncStatusRow(Icons.Outlined.Sync, stringResource(R.string.sync_in_progress))
            SyncUiStatus.WAITING -> SyncStatusRow(Icons.Outlined.CloudOff, stringResource(R.string.sync_waiting))
            SyncUiStatus.FAILED -> SyncStatusRow(Icons.Outlined.ErrorOutline, stringResource(R.string.sync_failed), emphasized = true)
            SyncUiStatus.SUCCEEDED, SyncUiStatus.IDLE ->
                if (lastSyncSuccessAtMs > 0L) {
                    SyncStatusRow(Icons.Outlined.CloudDone, stringResource(R.string.sync_done, formatSyncTime(lastSyncSuccessAtMs, currentLocale())))
                } else {
                    SyncStatusRow(Icons.Outlined.CloudQueue, stringResource(R.string.sync_never))
                }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onManualSync,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) { Text(if (syncStatus == SyncUiStatus.FAILED) stringResource(R.string.action_retry) else stringResource(R.string.sync_now), style = MaterialTheme.typography.labelMedium) }
            OutlinedButton(
                onClick = onLogout,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) { Text(stringResource(R.string.account_sign_out), style = MaterialTheme.typography.labelMedium) }
        }
        TextButton(
            onClick = onSwitchAccount,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.height(30.dp)
        ) { Text(stringResource(R.string.account_switch_kakao), style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun SyncStatusRow(icon: ImageVector, text: String, emphasized: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (emphasized) kr.co.nuni.gildam.ui.theme.WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (emphasized) kr.co.nuni.gildam.ui.theme.WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PhotoStorageFootnote(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.account_photos_not_synced),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.account_photos_storage_link),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
    }
}

private fun formatSyncTime(epochMs: Long, locale: java.util.Locale): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
        .format(shortDateTimeFormatter(locale))

private val KakaoYellow = Color(0xFFFEE500)
private val KakaoLabel = Color(0xD9000000) // 85% black (카카오 디자인 가이드)

/** 카카오 로그인 디자인 가이드: #FEE500 배경, 말풍선 심볼, 85% 검정 라벨, 모서리 12px. */
@Composable
internal fun KakaoLoginButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = KakaoYellow, contentColor = KakaoLabel)
    ) {
        KakaoSymbol(modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(7.dp))
        Text(stringResource(R.string.kakao_login), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = KakaoLabel)
    }
}

@Composable
private fun KakaoSymbol(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        drawRoundRect(Color.Black, size = Size(w, h * 0.82f), cornerRadius = CornerRadius(w * 0.45f))
        val tail = Path().apply {
            moveTo(w * 0.24f, h * 0.62f); lineTo(w * 0.18f, h); lineTo(w * 0.46f, h * 0.76f); close()
        }
        drawPath(tail, Color.Black)
    }
}

@Composable
private fun NuniFootnote() {
    Text(
        text = stringResource(R.string.account_nuni_id),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    )
}

internal fun shortSub(sub: String?): String =
    if (sub.isNullOrBlank()) "-" else if (sub.length <= 12) sub else sub.take(6) + "…" + sub.takeLast(4)

/** 닉네임 바꾸기: 앞뒤 공백을 빼고 1~20자 */
@Composable
private fun NicknameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val trimmed = text.trim()
    val valid = ProfilePrefs.normalize(text) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_nickname_change)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= ProfilePrefs.MAX_LENGTH + 10) text = it },
                singleLine = true,
                isError = text.isNotEmpty() && !valid,
                supportingText = {
                    Text(
                        if (text.isNotEmpty() && !valid) stringResource(R.string.account_nickname_length, ProfilePrefs.MAX_LENGTH)
                        else "${trimmed.length}/${ProfilePrefs.MAX_LENGTH}"
                    )
                },
                placeholder = { Text(stringResource(R.string.account_nickname)) }
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = valid) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
