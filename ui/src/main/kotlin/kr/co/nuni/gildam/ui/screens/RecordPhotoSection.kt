package kr.co.nuni.gildam.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kr.co.nuni.gildam.data.photo.MatchedPhoto
import kr.co.nuni.gildam.data.photo.RecordPhotoMatcher
import kr.co.nuni.gildam.ui.illustration.GildamTooltip
import kr.co.nuni.gildam.ui.illustration.TooltipIconButton
import kr.co.nuni.gildam.ui.illustration.IconButtonStyle

/**
 * 기록 상세의 '이 기록의 사진' 구역.
 * 기기 갤러리에서 기록 시간창 안에 찍힌 사진을 시간·위치로 짝지어 보여 줍니다.
 * 사진 파일은 복사·전송하지 않고 MediaStore URI 로만 표시합니다 (기기에만 남음).
 */
@Composable
internal fun RecordPhotoSection(
    loading: Boolean,
    permissionDenied: Boolean,
    photos: List<MatchedPhoto>,
    truncated: Boolean,
    onRequestPermission: () -> Unit
) {
    var viewing by remember { mutableStateOf<MatchedPhoto?>(null) }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "이 기록의 사진",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (!loading && !permissionDenied && photos.isNotEmpty()) {
                Text(
                    "${photos.size}장",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        when {
            loading -> Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("기기 사진에서 같은 시간·자리의 사진을 찾는 중…",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            permissionDenied -> PermissionsAskRow(onRequestPermission)
            photos.isEmpty() -> Text(
                "이 기록과 맞는 사진을 찾지 못했어요",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().height(
                        (((photos.size + 2) / 3) * 104).dp.coerceAtMost(312.dp)
                    ),
                    userScrollEnabled = false
                ) {
                    items(photos, key = { it.uri }) { p ->
                        PhotoThumb(p) { viewing = p }
                    }
                }
                if (truncated) {
                    Text(
                        "사진이 많아 앞쪽 ${photos.size}장만 확인했어요",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
        if (!permissionDenied) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.PhotoLibrary, null, modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(
                    "사진은 이 기기에만 있고, 동기화되지 않아요",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    viewing?.let { p ->
        PhotoViewerDialog(p, onClose = { viewing = null })
    }
}

/** 권한이 없을 때 한 줄 안내 + '사진 접근 허용' */
@Composable
private fun PermissionsAskRow(onRequestPermission: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ImageSearch, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "사진 접근을 허용하면 이 기록과 같은 시간·자리의 사진을 찾아 드려요.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            // 작은 버튼 (아이콘 크기 톤 유지): 누르면 시스템 권한 창
            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("사진 접근 허용", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PhotoThumb(p: MatchedPhoto, onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = Uri.parse(p.uri),
            contentDescription = "기록 사진",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (p.timeOnly) {
            // 시간만 맞춘 사진: 왼쪽 아래 작은 배지
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
            ) {
                Row(Modifier.padding(horizontal = 5.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocationOff, null, modifier = Modifier.size(10.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(3.dp))
                    Text("위치 정보 없음", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

/** 전체 화면 사진 보기: 촬영 시각·경로 위 위치, 작은 X */
@Composable
private fun PhotoViewerDialog(p: MatchedPhoto, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black.copy(alpha = 0.94f), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = Uri.parse(p.uri),
                    contentDescription = "사진 크게 보기",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(vertical = 56.dp)
                )
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(RecordPhotoMatcher.timeLabel(p.takenAtMs), style = MaterialTheme.typography.titleSmall, color = Color.White)
                        Text(
                            RecordPhotoMatcher.describePosition(p)?.let { pos ->
                                if (p.timeOnly) "$pos · 위치 정보 없음" else pos
                            } ?: "위치 정보 없음",
                            style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    TooltipIconButton(
                        Icons.Outlined.Close, "닫기", onClose,
                        style = IconButtonStyle.PLAIN, visualSize = 34.dp, iconSize = 20.dp
                    )
                }
            }
        }
    }
}
