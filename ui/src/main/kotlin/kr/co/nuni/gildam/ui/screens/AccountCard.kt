package kr.co.nuni.gildam.ui.screens

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
                text = "계정 · 동기화",
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
                    text = "경로와 메모를 다른 기기에서도 이어 보세요.",
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
                        Text("[디버그] 데모 세션 활성화", style = MaterialTheme.typography.labelSmall)
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
        text = "카카오 계정으로 로그인됨",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = nickname ?: "닉네임을 정해 주세요",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (nickname != null) FontWeight.SemiBold else FontWeight.Normal,
            color = if (nickname != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        GildamTooltip(label = "닉네임 바꾸기") {
            IconButton(onClick = { showNicknameDialog = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "닉네임 바꾸기",
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
            text = "서버에 저장된 기록 ${serverTrackCount}개",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(modifier = Modifier.height(6.dp))

    if (sessionState.requiresReauthBanner) {
        SyncStatusRow(
            icon = Icons.Outlined.ErrorOutline,
            text = "로그인이 만료됐어요 · 다시 로그인해 주세요",
            emphasized = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        KakaoLoginButton(onClick = onKakaoLogin)
        TextButton(
            onClick = onLogout,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.height(30.dp)
        ) { Text("로그아웃", style = MaterialTheme.typography.labelSmall) }
    } else {
        when (syncStatus) {
            SyncUiStatus.SYNCING -> SyncStatusRow(Icons.Outlined.Sync, "동기화 중…")
            SyncUiStatus.WAITING -> SyncStatusRow(Icons.Outlined.CloudOff, "동기화 대기 중 · 네트워크를 확인해요")
            SyncUiStatus.FAILED -> SyncStatusRow(Icons.Outlined.ErrorOutline, "동기화에 실패했어요", emphasized = true)
            SyncUiStatus.SUCCEEDED, SyncUiStatus.IDLE ->
                if (lastSyncSuccessAtMs > 0L) {
                    SyncStatusRow(Icons.Outlined.CloudDone, "동기화 완료 · 마지막 ${formatSyncTime(lastSyncSuccessAtMs)}")
                } else {
                    SyncStatusRow(Icons.Outlined.CloudQueue, "아직 동기화하지 않았어요")
                }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onManualSync,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) { Text(if (syncStatus == SyncUiStatus.FAILED) "다시 시도" else "지금 동기화", style = MaterialTheme.typography.labelMedium) }
            OutlinedButton(
                onClick = onLogout,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) { Text("로그아웃", style = MaterialTheme.typography.labelMedium) }
        }
        TextButton(
            onClick = onSwitchAccount,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.height(30.dp)
        ) { Text("다른 카카오 계정으로 전환", style = MaterialTheme.typography.labelSmall) }
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
            text = "사진 파일은 동기화되지 않아요",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = " · 보관 안내",
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

private fun formatSyncTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))

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
        Text("카카오 로그인", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = KakaoLabel)
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
        text = "NUNI ID로 연결해요",
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
        title = { Text("닉네임 바꾸기") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= ProfilePrefs.MAX_LENGTH + 10) text = it },
                singleLine = true,
                isError = text.isNotEmpty() && !valid,
                supportingText = {
                    Text(
                        if (text.isNotEmpty() && !valid) "1~${ProfilePrefs.MAX_LENGTH}자로 정해 주세요"
                        else "${trimmed.length}/${ProfilePrefs.MAX_LENGTH}"
                    )
                },
                placeholder = { Text("닉네임") }
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = valid) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
