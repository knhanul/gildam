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
    onLaunchNuniCustomTabLogin: () -> Unit,
    onDemoSimulateLogin: () -> Unit,
    onManualSync: () -> Unit,
    onLogout: () -> Unit,
    onClearThumbnailCache: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정 및 NUNI ID 동기화") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. NUNI ID 통합 로그인 & 동기화 카드
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "NUNI ID 통합 계정 (https://id.nuni.co.kr)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (sessionState.isLoggedIn) {
                        Text(
                            text = "🟢 연결됨: ${sessionState.userDisplayEmail ?: sessionState.nuniUserId}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "날씨 이날 등 다른 nuni 서비스와 함께 사용하는 계정입니다. 궤적과 메모만 안전하게 동기화됩니다.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onManualSync) {
                                Text("지금 동기화")
                            }
                            OutlinedButton(onClick = onLogout) {
                                Text("로그아웃")
                            }
                        }
                    } else {
                        Text(
                            text = "NUNI ID로 로그인하면 기기를 바꿔도 이동 궤적과 길 위의 메모를 그대로 이어서 볼 수 있습니다. (카카오 로그인도 NUNI ID 화면에서 지원)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onLaunchNuniCustomTabLogin,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("NUNI ID로 로그인 (Custom Tabs + PKCE)")
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = onDemoSimulateLogin,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("개발용 데모 세션 활성화 (스튜디오 테스트용)")
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
