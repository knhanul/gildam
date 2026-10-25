package kr.co.nuni.gildam.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.co.nuni.gildam.map.MapCameraMode
import kr.co.nuni.gildam.ui.illustration.brandGradient
import kr.co.nuni.gildam.ui.illustration.onBrand
import kr.co.nuni.gildam.ui.illustration.TooltipIconButton
import kr.co.nuni.gildam.ui.illustration.IconButtonStyle

/** 지도 위 카메라 모드 전환(따라가기/전체) + 직접 움직였을 때 '다시 맞추기' */
@Composable
fun MapCameraControls(
    mode: MapCameraMode,
    paused: Boolean,
    onSelect: (MapCameraMode) -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(paused, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            TooltipIconButton(Icons.Outlined.CenterFocusStrong, "다시 맞추기", onRecenter, style = IconButtonStyle.BRAND, visualSize = 40.dp, iconSize = 20.dp)
        }
        Spacer(Modifier.width(6.dp))
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), shadowElevation = 6.dp) {
            Row(Modifier.padding(horizontal = 2.dp)) {
                Seg(Icons.Outlined.MyLocation, "내 위치 따라가기", mode == MapCameraMode.FOLLOW && !paused) { onSelect(MapCameraMode.FOLLOW) }
                Seg(Icons.Outlined.Route, "전체 경로 보기", mode == MapCameraMode.OVERVIEW && !paused) { onSelect(MapCameraMode.OVERVIEW) }
            }
        }
    }
}

@Composable
private fun Seg(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    TooltipIconButton(icon, label, onClick, style = if (selected) IconButtonStyle.BRAND else IconButtonStyle.PLAIN, visualSize = 38.dp, iconSize = 20.dp)
}
