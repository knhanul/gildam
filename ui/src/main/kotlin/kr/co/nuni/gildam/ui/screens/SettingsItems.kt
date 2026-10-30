package kr.co.nuni.gildam.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 설정 그룹 제목 ("기록 설정" 등) — 아이보리 배경 위 작은 라벨 */
@Composable
internal fun SettingsGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp)
    )
}

/** 목록 항목들을 담는 라운드 카드 (그룹 하나당 카드 하나) */
@Composable
internal fun SettingsListCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

/**
 * 제목 + 보조 설명 + 오른쪽 값/화살표 목록 행.
 * 행 전체가 48dp 이상의 터치 영역이며, 글자 크기를 키우면 높이가 자동으로 늘어납니다.
 */
@Composable
internal fun SettingsListItem(
    title: String,
    supportingText: String? = null,
    valueText: String? = null,
    /** true 면 값을 주황색으로 강조 (권한 부족·확인된 제한 등 조치 필요) */
    valueEmphasis: Boolean = false,
    icon: ImageVector? = null,
    iconContent: (@Composable () -> Unit)? = null,
    /** 값 텍스트 대신 넣을 커스텀 트레일링 (예: 테마 색상 점) */
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconContent != null) {
            iconContent()
            Spacer(Modifier.width(12.dp))
        } else if (icon != null) {
            SettingsItemIcon(icon)
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailingContent != null) {
            trailingContent()
            Spacer(Modifier.width(2.dp))
        }
        if (valueText != null) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (valueEmphasis) FontWeight.SemiBold else FontWeight.Normal,
                color = if (valueEmphasis) kr.co.nuni.gildam.ui.theme.WarningAmber
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(2.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/** 목록 행 앞의 라운드 아이콘 (기본색을 옅게 깐 배경 + 기본색 아이콘) */
@Composable
internal fun SettingsItemIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(19.dp)
        )
    }
}

/** 카드 안 행 사이 구분선 */
@Composable
internal fun SettingsItemDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 60.dp, end = 14.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
}
