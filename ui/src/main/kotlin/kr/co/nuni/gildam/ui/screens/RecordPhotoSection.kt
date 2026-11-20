package kr.co.nuni.gildam.ui.screens

import androidx.compose.ui.platform.LocalContext
import kr.co.nuni.gildam.ui.l10n.*
import androidx.compose.ui.res.stringResource
import kr.co.nuni.gildam.ui.R
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.InputChip
import androidx.compose.material3.TextButton
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

/** 사진 구역 위 '○○ 사진만' 필터 (길 위의 이야기 한 곳을 눌렀을 때) */
internal data class PhotoPlaceFilter(
    val waypointId: String,
    val label: String,
    val photos: List<MatchedPhoto>
)

/** 접힌 상태에서 보여 줄 줄 수 (3장씩) */
internal const val RECORD_PHOTO_COLLAPSED_ROWS = 4
private const val PHOTO_COLUMNS = 3

/**
 * 기록 상세의 '이 기록의 사진' 구역 (LazyColumn 안에 줄 단위 item 으로 넣음).
 * 기기 갤러리에서 기록 시간창 안에 찍힌 사진을 시간·위치로 짝지어 보여 줍니다.
 * 사진 파일은 복사·전송하지 않고 MediaStore URI 로만 표시합니다 (기기에만 남음).
 *
 * - 처음엔 [RECORD_PHOTO_COLLAPSED_ROWS] 줄만, '전체 보기'로 모두 펼침 (줄마다 item 이라 많아도 가볍게 스크롤)
 * - [filter] 가 있으면 그곳 사진만 + '○○ 사진만 ✕' 칩
 */
internal fun LazyListScope.recordPhotoSectionItems(
    loading: Boolean,
    permissionDenied: Boolean,
    photos: List<MatchedPhoto>,
    truncated: Boolean,
    locationRedacted: Boolean,
    expanded: Boolean,
    filter: PhotoPlaceFilter?,
    onToggleExpanded: () -> Unit,
    onClearFilter: () -> Unit,
    onOpenPhoto: (MatchedPhoto) -> Unit,
    onRequestPermission: () -> Unit,
    onRequestLocationAccess: () -> Unit
) {
    val shown = filter?.photos ?: photos
    item(key = "photos_header") {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.record_photos_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                if (!loading && !permissionDenied && photos.isNotEmpty()) {
                    Text(
                        stringResource(R.string.record_photos_count, shown.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (filter != null) {
                InputChip(
                    selected = true,
                    onClick = onClearFilter,
                    label = { Text(stringResource(R.string.record_photos_filter_chip, filter.label), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Outlined.Place, null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        Icon(Icons.Outlined.Close, stringResource(R.string.record_photos_filter_clear), modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            when {
                loading -> Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.record_photos_searching),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                permissionDenied -> PermissionsAskRow(
                    stringResource(R.string.record_photos_permission_desc),
                    stringResource(R.string.record_photos_permission_allow),
                    onRequestPermission
                )
                photos.isEmpty() -> Text(
                    stringResource(R.string.record_photos_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                filter != null && filter.photos.isEmpty() -> Text(
                    stringResource(R.string.record_photos_filter_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                else -> if (locationRedacted) {
                    PermissionsAskRow(
                        stringResource(R.string.record_photos_location_desc),
                        stringResource(R.string.record_photos_location_allow),
                        onRequestLocationAccess,
                        icon = Icons.Outlined.LocationOn
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
    if (loading || permissionDenied || shown.isEmpty()) {
        item(key = "photos_footer") { PhotoLocalOnlyNote(permissionDenied) }
        return
    }
    val rows = shown.chunked(PHOTO_COLUMNS)
    // 필터 중(보통 몇 장)이거나 4줄 이하면 접지 않음
    val collapsible = filter == null && rows.size > RECORD_PHOTO_COLLAPSED_ROWS
    val visibleRows = if (collapsible && !expanded) rows.take(RECORD_PHOTO_COLLAPSED_ROWS) else rows
    items(visibleRows.size, key = { i -> "photos_row_" + visibleRows[i].first().uri }) { i ->
        PhotoRow(visibleRows[i], onOpenPhoto)
    }
    item(key = "photos_footer") {
        Column(Modifier.fillMaxWidth()) {
            if (collapsible) {
                TextButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        null, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (expanded) stringResource(R.string.record_photos_show_less)
                        else stringResource(R.string.record_photos_show_all, shown.size)
                    )
                }
            }
            if (truncated) {
                Text(
                    stringResource(R.string.record_photos_capped, photos.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            PhotoLocalOnlyNote(false)
        }
    }
}

/** 한 줄(최대 3장). 모자란 칸은 빈 자리로 둬 크기를 맞춤 */
@Composable
private fun PhotoRow(row: List<MatchedPhoto>, onOpenPhoto: (MatchedPhoto) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        row.forEach { p ->
            Box(Modifier.weight(1f)) { PhotoThumb(p) { onOpenPhoto(p) } }
        }
        repeat(PHOTO_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun PhotoLocalOnlyNote(permissionDenied: Boolean) {
    if (permissionDenied) return
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.PhotoLibrary, null, modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.record_photos_local_only),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 권한이 없을 때 한 줄 안내 + '사진 접근 허용' */
@Composable
private fun PermissionsAskRow(
    desc: String,
    action: String,
    onRequestPermission: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Outlined.ImageSearch
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                desc,
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
                Text(action, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
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
            contentDescription = stringResource(R.string.record_photo_cd),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (p.timeOnly) {
            // GPS 없는 사진: 경로 기준 추정이면 '경로 기준 위치', 그것도 없으면 '위치 정보 없음'
            val estimated = p.routeEstimated
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
            ) {
                Row(Modifier.padding(horizontal = 5.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (estimated) Icons.Outlined.Route else Icons.Outlined.LocationOff, null, modifier = Modifier.size(10.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(3.dp))
                    Text(
                        stringResource(if (estimated) R.string.photo_route_estimated else R.string.photo_no_location),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                    )
                }
            }
        }
    }
}

/** 전체 화면 사진 보기: 촬영 시각·경로 위 위치, 작은 X (기록 사진 목록·지도 핀 공용) */
@Composable
internal fun PhotoViewerDialog(p: MatchedPhoto, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black.copy(alpha = 0.94f), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = Uri.parse(p.uri),
                    contentDescription = stringResource(R.string.photo_enlarge_cd),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(vertical = 56.dp)
                )
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(formatShortTime(LocalContext.current, p.takenAtMs), style = MaterialTheme.typography.titleSmall, color = Color.White)
                        Text(
                            describePhotoPosition(LocalContext.current, p.alongTrackM, p.offsetM)?.let { pos ->
                                if (p.timeOnly) stringResource(R.string.photo_pos_route_estimated, pos) else pos
                            } ?: stringResource(R.string.photo_no_location),
                            style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                    TooltipIconButton(
                        Icons.Outlined.Close, stringResource(R.string.action_close), onClose,
                        style = IconButtonStyle.PLAIN, visualSize = 34.dp, iconSize = 20.dp
                    )
                }
            }
        }
    }
}
