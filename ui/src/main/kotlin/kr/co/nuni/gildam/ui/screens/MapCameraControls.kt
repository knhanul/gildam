package kr.co.nuni.gildam.ui.screens

import androidx.compose.ui.res.stringResource
import kr.co.nuni.gildam.ui.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kr.co.nuni.gildam.map.MapCameraMode
import kr.co.nuni.gildam.ui.illustration.GildamTooltip
import kr.co.nuni.gildam.ui.illustration.IconButtonStyle
import kr.co.nuni.gildam.ui.illustration.TooltipIconButton

/** 기록 상태 점: 초록=정상 기록 중, 회색=일시정지, 주황=GPS 약함 같은 문제 */
enum class RecordingStatus(val color: Color, val titleRes: Int, val detailRes: Int) {
    RECORDING(Color(0xFF2E9E5B), R.string.status_recording, R.string.status_recording_desc),
    PAUSED(Color(0xFF8A8F8C), R.string.status_paused, R.string.status_paused_desc),
    GPS_WEAK(Color(0xFFE8831A), R.string.status_gps_weak, R.string.gps_searching_desc);


    val title: String @Composable get() = stringResource(titleRes)
    val detail: String @Composable get() = stringResource(detailRes)
    companion object {
        fun of(isPaused: Boolean, gpsWeak: Boolean): RecordingStatus = when {
            gpsWeak -> GPS_WEAK
            isPaused -> PAUSED
            else -> RECORDING
        }
    }
}

/**
 * 기록 중 지도 상단 한 줄: [상태 점] ……… [다시 맞추기] [전체 경로 | 현재 경로] [나침반]
 * - 경로 버튼 2개: 전체 경로 보기 / 현재 경로 보기
 * - 나침반: 북쪽 위 ↔ 주행 방향 위. 코스 따라 걷기 중에는 주행 방향 위만 쓰므로 숨김
 */
@Composable
fun MapCameraControls(
    mode: MapCameraMode,
    paused: Boolean,
    onSelect: (MapCameraMode) -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier,
    /** 나침반(북쪽 위 전환) 버튼을 보일지 */
    allowNorthUp: Boolean = true,
    /** 현재 지도 회전각(도). 주행 방향 위일 때 나침반 바늘이 북쪽을 가리키도록 돌림 */
    mapBearingDeg: Float = 0f,
    /** 기록 중이면 왼쪽에 상태 점 */
    status: RecordingStatus? = null,
    /** 점검에서 찾은 문제들. 있으면 점이 주황/빨강 + 옆에 짧은 글 */
    issues: List<RecordingIssue> = emptyList(),
    /** 점·짧은 글을 누르면 자세히 보기 */
    onOpenIssues: () -> Unit = {},
    /** 현재 경로 보기를 누를 때 고를 방향 (마지막으로 쓴 북쪽 위/주행 방향 위) */
    followMode: MapCameraMode = if (mode.isFollow) mode else MapCameraMode.HEADING
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // 점 + 짧은 글은 버튼 앞 남는 폭만 쓰고, 넘치면 '…' 로 잘라 버튼을 가리지 않음
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (issues.isNotEmpty()) {
                IssueDotWithText(issues, onOpenIssues)
            } else if (status != null) {
                RecordingStatusDot(status)
            }
        }
        AnimatedVisibility(paused, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            TooltipIconButton(Icons.Outlined.CenterFocusStrong, stringResource(R.string.map_recenter), onRecenter, style = IconButtonStyle.BRAND, visualSize = 40.dp, iconSize = 20.dp)
        }
        Spacer(Modifier.width(6.dp))
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), shadowElevation = 6.dp) {
            Row(Modifier.padding(horizontal = 2.dp)) {
                Seg(Icons.Outlined.Route, stringResource(R.string.map_mode_overview), mode == MapCameraMode.OVERVIEW && !paused) { onSelect(MapCameraMode.OVERVIEW) }
                Seg(Icons.Outlined.MyLocation, stringResource(R.string.map_mode_follow), mode.isFollow && !paused) {
                    onSelect(if (allowNorthUp) followMode else MapCameraMode.HEADING)
                }
            }
        }
        if (allowNorthUp) {
            Spacer(Modifier.width(6.dp))
            CompassToggle(
                // 전체 경로 보기는 경로에 맞춰 돌아갈 수 있으므로 바늘이 진짜 북쪽을 가리킴
                northUp = mode == MapCameraMode.FOLLOW,
                mapBearingDeg = if (mode == MapCameraMode.FOLLOW) 0f else mapBearingDeg,
                onClick = { onSelect(MapCameraMode.toggleCompass(if (mode.isFollow) mode else followMode)) }
            )
        }
    }
}

@Composable
private fun Seg(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    TooltipIconButton(icon, label, onClick, style = if (selected) IconButtonStyle.BRAND else IconButtonStyle.PLAIN, visualSize = 38.dp, iconSize = 20.dp)
}

/**
 * 나침반 버튼. 주행 방향 위: 바늘이 지도 회전에 따라 북쪽을 가리키며 돎.
 * 북쪽 위: 바늘은 위로 고정, 'N' 글자를 함께 표시.
 */
@Composable
fun CompassToggle(northUp: Boolean, mapBearingDeg: Float, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val label = if (northUp) stringResource(R.string.compass_north_up) else stringResource(R.string.compass_heading_up)
    GildamTooltip(label) {
        Surface(
            shape = CircleShape,
            color = cs.surface.copy(alpha = 0.95f),
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label }
        ) {
            Box(contentAlignment = Alignment.Center) {
                val north = Color(0xFFD8452E)
                val south = cs.onSurfaceVariant.copy(alpha = 0.55f)
                Canvas(Modifier.padding(top = if (northUp) 9.dp else 0.dp).size(if (northUp) 30.dp else 34.dp).rotate(if (northUp) 0f else -mapBearingDeg)) {
                    val cx = size.width / 2; val cy = size.height / 2; val w = size.width * 0.2f
                    val top = Path().apply { moveTo(cx, 0f); lineTo(cx + w, cy); lineTo(cx - w, cy); close() }
                    val bot = Path().apply { moveTo(cx, size.height); lineTo(cx + w, cy); lineTo(cx - w, cy); close() }
                    drawPath(top, north); drawPath(bot, south)
                    drawCircle(Color.White, w * 0.45f, Offset(cx, cy))
                }
                if (northUp) {
                    Text(
                        "N", fontSize = 12.sp, fontWeight = FontWeight.Black, color = north,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 0.dp)
                    )
                }
            }
        }
    }
}

/** 작은 상태 점. 누르면(또는 길게 누르면) 자세한 상태를 말풍선으로 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingStatusDot(status: RecordingStatus, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val statusCd = "${status.title}. ${status.detail}"
    val previewOpen = kr.co.nuni.gildam.ui.illustration.LocalTooltipPreviewLabel.current == status.title
    val tip = rememberTooltipState(initialIsVisible = previewOpen, isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = StartAlignedBelowProvider,
        tooltip = {
            PlainTooltip(modifier = Modifier.widthIn(max = 280.dp), shape = RoundedCornerShape(12.dp), containerColor = cs.inverseSurface, contentColor = cs.inverseOnSurface) {
                Column(Modifier.padding(2.dp)) {
                    Text(status.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(status.detail, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        state = tip
    ) {
        Box(
            modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) { scope.launch { tip.show() } }
                .semantics { contentDescription = statusCd },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .background(cs.surface.copy(alpha = 0.95f), CircleShape)
                    .border(1.dp, status.color.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(14.dp).background(status.color, CircleShape))
            }
        }
    }
}

/** 상태 점 말풍선: 점 아래, 왼쪽 끝을 맞춰 화면 밖으로 잘리지 않게 */
private object StartAlignedBelowProvider : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize
    ): androidx.compose.ui.unit.IntOffset {
        val x = anchorBounds.left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = anchorBounds.bottom + 12
        return androidx.compose.ui.unit.IntOffset(x, y)
    }
}

/** 문제 상태 색: 심각(위치 꺼짐·권한 없음) 빨강, 나머지 주황 */
fun List<RecordingIssue>.dotColor(): Color =
    if (any { it.severe }) Color(0xFFD93A2B) else Color(0xFFE8831A)

/** 문제 점 + 짧은 글 (예: '배터리 최적화 외 2'). 누르면 자세히 보기 */
@Composable
fun IssueDotWithText(issues: List<RecordingIssue>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val color = issues.dotColor()
    val summary = issues.shortSummary() ?: return
    val badgeCd = stringResource(R.string.health_badge_cd, summary)
    Surface(
        shape = RoundedCornerShape(50),
        color = cs.surface.copy(alpha = 0.95f),
        shadowElevation = 4.dp,
        modifier = modifier
            .padding(end = 6.dp)
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = badgeCd }
    ) {
        Row(Modifier.padding(start = 7.dp, end = 10.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                summary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

/** 자세히 보기 내용: 문제마다 전체 설명 + '설정으로 가기' */
@Composable
fun RecordingIssuesDetail(issues: List<RecordingIssue>, onOpenSettings: (RecordingIssue) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.health_sheet_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(4.dp))
        Text(
            stringResource(R.string.health_sheet_desc),
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
        )
        Spacer(Modifier.size(12.dp))
        issues.forEach { issue ->
            Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 4.dp).size(12.dp).background(listOf(issue).dotColor(), CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(issue.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.size(2.dp))
                        Text(issue.message, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        if (issue.hasSettings) {
                            Spacer(Modifier.size(8.dp))
                            androidx.compose.material3.FilledTonalButton(
                                onClick = { onOpenSettings(issue) },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                            ) { Text(stringResource(R.string.health_go_settings), style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.size(12.dp))
    }
}
