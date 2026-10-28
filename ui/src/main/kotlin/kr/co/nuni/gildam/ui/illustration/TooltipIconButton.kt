package kr.co.nuni.gildam.ui.illustration

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 테스트/미리보기에서 특정 말풍선을 처음부터 띄울 때 사용 (라벨 일치) */
val LocalTooltipPreviewLabel = compositionLocalOf<String?> { null }

enum class IconButtonStyle { BRAND, TONAL, OUTLINED, PLAIN }

/** 테마 색 말풍선. [detail] 을 주면 이름 아래에 작은 설명을 함께 보여 줍니다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GildamTooltip(label: String, detail: String? = null, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(spacingBetweenTooltipAndAnchor = 6.dp),
        tooltip = {
            PlainTooltip(
                caretSize = TooltipDefaults.caretSize,
                shape = RoundedCornerShape(12.dp),
                containerColor = cs.inverseSurface,
                contentColor = cs.inverseOnSurface,
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)) {
                    Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    if (detail != null) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.inverseOnSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        },
        state = rememberTooltipState(initialIsVisible = LocalTooltipPreviewLabel.current == label, isPersistent = LocalTooltipPreviewLabel.current == label)
    ) { content() }
}

/**
 * 작은 아이콘 버튼 + 길게 누르면 이름 말풍선.
 * 보이는 크기는 [visualSize], 터치 영역은 최소 48dp.
 */
@Composable
fun TooltipIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: IconButtonStyle = IconButtonStyle.TONAL,
    visualSize: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    enabled: Boolean = true
) {
    val cs = MaterialTheme.colorScheme
    val fg: Color = when (style) {
        IconButtonStyle.BRAND -> onBrand()
        IconButtonStyle.TONAL -> cs.onSecondaryContainer
        IconButtonStyle.OUTLINED, IconButtonStyle.PLAIN -> cs.primary
    }
    GildamTooltip(label) {
        Box(
            modifier.minimumInteractiveComponentSize()
                .semantics { contentDescription = label; role = Role.Button }
                .clip(CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            val base = Modifier.size(visualSize)
            val styled = when (style) {
                IconButtonStyle.BRAND -> base.shadow(6.dp, CircleShape, ambientColor = cs.primary, spotColor = cs.primary).clip(CircleShape).background(brandGradient())
                IconButtonStyle.TONAL -> base.clip(CircleShape).background(cs.secondaryContainer)
                IconButtonStyle.OUTLINED -> base.clip(CircleShape).background(cs.surface).border(1.5.dp, cs.primary.copy(alpha = 0.6f), CircleShape)
                IconButtonStyle.PLAIN -> base
            }
            Box(styled, contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(iconSize))
            }
        }
    }
}
