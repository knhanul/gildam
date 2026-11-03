package kr.co.nuni.gildam.ui.screens

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
enum class RecordingStatus(val color: Color, val title: String, val detail: String) {
    RECORDING(Color(0xFF2E9E5B), "기록 중", "백그라운드 보호 활성 · 화면이 꺼져도 1초 주기로 궤적을 기록합니다"),
    PAUSED(Color(0xFF8A8F8C), "일시정지됨", "다시 시작하면 이어서 기록합니다"),
    GPS_WEAK(Color(0xFFE8831A), "GPS 신호 약함", "GPS 신호를 찾고 있어요. 마지막 확인 위치부터 이어집니다.");

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
    /** 현재 경로 보기를 누를 때 고를 방향 (마지막으로 쓴 북쪽 위/주행 방향 위) */
    followMode: MapCameraMode = if (mode.isFollow) mode else MapCameraMode.HEADING
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (status != null) RecordingStatusDot(status)
        Spacer(Modifier.weight(1f))
        AnimatedVisibility(paused, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            TooltipIconButton(Icons.Outlined.CenterFocusStrong, "다시 맞추기", onRecenter, style = IconButtonStyle.BRAND, visualSize = 40.dp, iconSize = 20.dp)
        }
        Spacer(Modifier.width(6.dp))
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), shadowElevation = 6.dp) {
            Row(Modifier.padding(horizontal = 2.dp)) {
                Seg(Icons.Outlined.Route, "전체 경로 보기", mode == MapCameraMode.OVERVIEW && !paused) { onSelect(MapCameraMode.OVERVIEW) }
                Seg(Icons.Outlined.MyLocation, "현재 경로 보기", mode.isFollow && !paused) {
                    onSelect(if (allowNorthUp) followMode else MapCameraMode.HEADING)
                }
            }
        }
        if (allowNorthUp) {
            Spacer(Modifier.width(6.dp))
            CompassToggle(
                northUp = mode == MapCameraMode.FOLLOW || (mode == MapCameraMode.OVERVIEW),
                mapBearingDeg = if (mode == MapCameraMode.HEADING) mapBearingDeg else 0f,
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
    val label = if (northUp) "북쪽 위 · 누르면 주행 방향 위로" else "주행 방향 위 · 누르면 북쪽 위로"
    GildamTooltip(label) {
        Surface(
            shape = CircleShape,
            color = cs.surface.copy(alpha = 0.95f),
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label }
        ) {
            Box(contentAlignment = Alignment.Center) {
                val north = Color(0xFFD8452E)
                val south = cs.onSurfaceVariant.copy(alpha = 0.55f)
                Canvas(Modifier.size(26.dp).rotate(if (northUp) 0f else -mapBearingDeg)) {
                    val cx = size.width / 2; val cy = size.height / 2; val w = size.width * 0.2f
                    val top = Path().apply { moveTo(cx, 0f); lineTo(cx + w, cy); lineTo(cx - w, cy); close() }
                    val bot = Path().apply { moveTo(cx, size.height); lineTo(cx + w, cy); lineTo(cx - w, cy); close() }
                    drawPath(top, north); drawPath(bot, south)
                    drawCircle(Color.White, w * 0.45f, Offset(cx, cy))
                }
                if (northUp) {
                    Text(
                        "N", fontSize = 9.sp, fontWeight = FontWeight.Black, color = north,
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
                .semantics { contentDescription = "${status.title}. ${status.detail}" },
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
