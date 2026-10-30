package kr.co.nuni.gildam.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 설정 > 데이터 보관 > 사진 보관 안내 상세 화면.
 * 실제 저장 구조: 갤러리(MediaStore) 원본 참조만 저장하고, 사진 파일·썸네일은 서버로 올리지 않습니다.
 * 동기화되는 것은 메모와 '사진이 있다'는 표시(photoLocalOnly/photoCount)뿐입니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoStorageScreen(
    thumbnailCacheBytes: Long,
    onClearThumbnailCache: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("사진 보관 안내") },
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(2.dp))

            InfoCard {
                Bullet("사진 파일은 길담 서버로 올리거나 다른 기기와 동기화하지 않아요.")
                Bullet("사진은 앱 폴더로 복사하지 않고, 갤러리 속 원본에 연결해 두고 보여 줘요.")
                Bullet("앱을 지워도 갤러리의 원본 사진은 그대로예요. 기록·사진 연결 등 앱 안의 데이터는 함께 지워져요.")
                Bullet("갤러리에서 사진을 지우거나 사진 접근 권한이 바뀌면 길담에서 사진을 열 수 없어요. 그래도 길 위의 메모와 기록은 남아 있어요.")
                Bullet("다른 기기에서 기록을 동기화해도 '사진이 있다'는 표시와 메모만 이어져요. 이 기기 갤러리의 사진 연결은 새 기기에서 자동으로 복원되지 않아요.")
                Bullet("Google 포토 등 다른 서비스로 사진을 백업했다면, 그 서비스 설정에 따라 사진이 관리돼요.")
            }

            // 썸네일 캐시: 목록·지도 표시 속도를 위한 로컬 미리보기. 지워도 원본·메모는 안전합니다.
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsItemIcon(Icons.Outlined.PhotoLibrary)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("썸네일 캐시", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (thumbnailCacheBytes > 0) "현재 ${formatBytes(thumbnailCacheBytes)} 사용 중"
                            else "삭제할 캐시가 없어요",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "목록과 지도에 사진을 빠르게 보여 주려고 만든 작은 미리보기예요. 지워도 원본 사진과 메모는 삭제되지 않아요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onClearThumbnailCache,
                    enabled = thumbnailCacheBytes > 0
                ) { Text("캐시 비우기") }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Bullet(text: String) {
    Row {
        Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
