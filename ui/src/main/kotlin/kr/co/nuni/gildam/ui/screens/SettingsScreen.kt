package kr.co.nuni.gildam.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import kr.co.nuni.gildam.ui.theme.DarkModePref
import kr.co.nuni.gildam.ui.theme.GildamPalette
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kr.co.nuni.gildam.auth.NuniSessionState

/**
 * 설정 및 NUNI ID 로그인 화면 (확정된 사진 정책 필수 고지 문구 포함).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sessionState: NuniSessionState,
    thumbnailCacheBytes: Long,
    isBatteryOptimizationIgnored: Boolean,
    onKakaoLogin: () -> Unit,
    onSwitchAccount: () -> Unit,
    /** 디버그 빌드에서만 전달 (릴리스는 null → 버튼 숨김) */
    onDemoSimulateLogin: (() -> Unit)?,
    onManualSync: () -> Unit,
    onLogout: () -> Unit,
    onClearThumbnailCache: () -> Unit,
    onNavigateBack: () -> Unit,
    /** 로그인 후 서버(GET /v1/me)에 저장된 기록 수, 모르면 null */
    serverTrackCount: Int? = null,
    /** 하단 탭바가 있을 때는 뒤로 아이콘을 숨김 */
    isTopLevelTab: Boolean = false,
    palette: GildamPalette = GildamPalette.DEFAULT,
    darkMode: DarkModePref = DarkModePref.SYSTEM,
    onSelectPalette: (GildamPalette) -> Unit = {},
    onSelectDarkMode: (DarkModePref) -> Unit = {}
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    if (!isTopLevelTab) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
                        }
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ThemeSection(palette, darkMode, onSelectPalette, onSelectDarkMode)

            // 1. 계정 카드: 사용자에게는 카카오 로그인, 내부적으로는 NUNI ID(id.nuni.co.kr)
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "계정", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    if (sessionState.isLoggedIn) {
                        Text(
                            text = "🟢 카카오 계정으로 로그인됨",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "회원번호 ${shortSub(sessionState.nuniUserId)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (serverTrackCount != null) "☁️ 서버에 저장된 기록 ${serverTrackCount}개" else "☁️ 서버 기록 확인 중…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (sessionState.requiresReauthBanner) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "⚠️ 로그인이 만료됐어요. 다시 로그인하면 동기화가 이어져요.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            KakaoLoginButton(onClick = onKakaoLogin)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onManualSync) { Text("지금 동기화") }
                            OutlinedButton(onClick = onLogout) { Text("로그아웃") }
                        }
                        TextButton(onClick = onSwitchAccount) { Text("다른 카카오 계정으로 전환") }
                        NuniFootnote()
                    } else {
                        Text(
                            text = "로그인하면 기기를 바꿔도 이동 궤적과 길 위의 메모를 그대로 이어서 볼 수 있어요. 사진은 동기화되지 않아요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        KakaoLoginButton(onClick = onKakaoLogin)
                        NuniFootnote()
                        if (onDemoSimulateLogin != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(onClick = onDemoSimulateLogin, modifier = Modifier.fillMaxWidth()) {
                                Text("[디버그] 데모 세션 활성화")
                            }
                        }
                    }
                }
            }

            // 2. 확정된 사진 보관 정책 고정 안내 카드 (필수 문구 100% 반영)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📌 사진 보관 정책 안내",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "사진은 이 기기에만 저장 — 사진 파일은 NUNI ID와 동기화되지 않습니다. 다른 기기에서는 사진을 볼 수 없습니다.",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• 길담은 사진을 앱 폴더로 복사하지 않고 갤러리(MediaStore URI)로만 연결합니다.\n• 앱을 삭제해도 사진은 갤러리에 남으며, 갤러리에서 사진을 지우면 길담에서도 볼 수 없고 메모만 남습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(onClick = onClearThumbnailCache) {
                        val kb = thumbnailCacheBytes / 1024
                        Text("로컬 전용 썸네일 캐시 비우기 (현재 ${kb} KB)")
                    }
                }
            }

            // 3. 제조사 배터리 최적화(삼성 One UI 등) 해제 안내 UX
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "백그라운드 기록 안정성 & 배터리 최적화",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isBatteryOptimizationIgnored) {
                            "✓ 배터리 사용량 '제한 없음' 설정이 완료되어 화면이 꺼져도 기록이 안전하게 유지됩니다."
                        } else {
                            "⚠️ 삼성 One UI 등의 절전 기능이 화면이 꺼진 뒤 GPS 기록을 중단시킬 수 있습니다. 앱 정보 > 배터리에서 '제한 없음'으로 설정해 주세요."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Text("시스템 앱 권한 및 배터리 설정 열기")
                    }
                }
            }
        }
    }
}

private val KakaoYellow = Color(0xFFFEE500)
private val KakaoLabel = Color(0xD9000000) // 85% black (카카오 디자인 가이드)

/** 카카오 로그인 디자인 가이드: #FEE500 배경, 검정 말풍선 심볼, 85% 검정 라벨, 모서리 12px. */
@Composable
internal fun KakaoLoginButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = KakaoYellow, contentColor = KakaoLabel)
    ) {
        KakaoSymbol(modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("카카오로 시작하기", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = KakaoLabel)
    }
}

@Composable
private fun KakaoSymbol(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        drawOval(Color.Black, topLeft = Offset(0f, 0f), size = Size(w, h * 0.82f))
        val tail = Path().apply {
            moveTo(w * 0.24f, h * 0.62f); lineTo(w * 0.18f, h); lineTo(w * 0.46f, h * 0.76f); close()
        }
        drawPath(tail, Color.Black)
    }
}

@Composable
private fun NuniFootnote() {
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "누니 통합계정(NUNI ID)으로 안전하게 연결돼요",
        style = MaterialTheme.typography.labelSmall,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

internal fun shortSub(sub: String?): String =
    if (sub.isNullOrBlank()) "-" else if (sub.length <= 12) sub else sub.take(6) + "…" + sub.takeLast(4)
