package kr.co.nuni.gildam.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kr.co.nuni.gildam.data.photo.MatchedPhoto
import kr.co.nuni.gildam.map.GildamMapLibreView
import kr.co.nuni.gildam.map.MapPhotoMarker
import kr.co.nuni.gildam.recording.LatLonPoint
import kr.co.nuni.gildam.ui.illustration.IconButtonStyle
import kr.co.nuni.gildam.ui.illustration.TooltipIconButton

/**
 * 기록 상세 지도 전체 화면.
 * - 경로 전체가 한 화면에 들어오게 맞춤(fitToTrack), 두 손가락 회전·확대·나침반 허용
 * - 기록에 붙은 사진은 그 자리에 작은 원형 핀으로 표시하고, 누르면 사진을 엽니다
 * - 사진은 기기에만 있고, 지도는 URI 로 읽은 썸네일만 메모리에 씁니다
 */
@Composable
internal fun RecordMapFullscreen(
    title: String,
    route: List<LatLonPoint>,
    photos: List<MatchedPhoto>,
    onClose: () -> Unit
) {
    val pins = remember(photos) {
        photos.mapNotNull { p ->
            val lat = p.pointLat ?: return@mapNotNull null
            val lon = p.pointLon ?: return@mapNotNull null
            MapPhotoMarker(id = p.uri, latitude = lat, longitude = lon, uri = p.uri, timeOnly = p.timeOnly)
        }
    }
    var viewing by remember { mutableStateOf<MatchedPhoto?>(null) }

    BackHandler(onBack = onClose)
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                GildamMapLibreView(
                    trackPoints = route,
                    currentLocation = null,
                    fitToTrack = true,
                    fullscreenGestures = true,
                    photoMarkers = pins,
                    onPhotoMarkerTap = { id -> viewing = photos.firstOrNull { it.uri == id } },
                    modifier = Modifier.fillMaxSize()
                )
                // 기록 제목 + 사진 수 + 닫기 (지도 위 작은 오버레이)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), androidx.compose.ui.graphics.Color.Transparent)
                            )
                        )
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (pins.isNotEmpty()) {
                            Text(
                                "사진 ${pins.size}장 표시 · 핀을 누르면 크게 볼 수 있어요",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    TooltipIconButton(
                        Icons.Outlined.Close, "지도 닫기", onClose,
                        style = IconButtonStyle.PLAIN, visualSize = 36.dp, iconSize = 20.dp
                    )
                }
                // 지도 조작 안내 (작게, 아래쪽)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp)
                ) {
                    Text(
                        "두 손가락으로 돌리고, 벌려서 확대해 보세요",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    viewing?.let { p -> PhotoViewerDialog(p, onClose = { viewing = null }) }
}
