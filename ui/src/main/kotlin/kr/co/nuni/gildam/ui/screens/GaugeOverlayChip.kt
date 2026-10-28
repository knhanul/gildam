package kr.co.nuni.gildam.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AvTimer
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.nuni.gildam.data.model.GaugeMetric
import kr.co.nuni.gildam.recording.LiveRecordingState
import kr.co.nuni.gildam.ui.illustration.GildamTooltip
import kr.co.nuni.gildam.ui.illustration.brandGradient
import kr.co.nuni.gildam.ui.illustration.onBrand
import kr.co.nuni.gildam.ui.theme.TabularStatMediumStyle

/** 계기판 값별 아이콘 */
val GaugeMetric.icon: ImageVector
    get() = when (this) {
        GaugeMetric.DISTANCE -> Icons.Outlined.Straighten
        GaugeMetric.TOTAL_TIME -> Icons.Outlined.Timer
        GaugeMetric.CURRENT_SPEED -> Icons.Outlined.Speed
        GaugeMetric.MOVING_AVG_SPEED -> Icons.Outlined.AvTimer
        GaugeMetric.CLOCK -> Icons.Outlined.Schedule
    }

/** 말풍선에 쓰는 계기판 이름 (테스트에서 라벨로 찾음) */
fun GaugeMetric.tooltipLabel(): String = "지도 위 계기판 · ${labelKo}"

/**
 * 지도 위에 겹쳐 보이는 계기판 칩.
 * - 밝은 지도·어두운 지도 어디에서도 보이도록 테마 표면색 + 테마 기본색 테두리 + 그림자를 함께 씁니다.
 * - 길게 누르면 어떤 값인지 말풍선이 뜹니다(작은 버튼 정책과 동일, 터치 영역 48dp 이상).
 */
@Composable
fun GaugeOverlayChip(
    metric: GaugeMetric,
    live: LiveRecordingState,
    clockTick: androidx.compose.runtime.State<Long>,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val label = metric.tooltipLabel()
    // 현재 시간일 때만 시각 상태를 구독합니다 (나머지 값은 시각과 무관).
    val value = gaugeValue(metric, live, clockTick.value)
    val spoken = "${metric.labelKo} $value${metric.unit?.let { " $it" } ?: ""}"
    // 길게 누르면 어떤 값인지 말풍선으로 보여 줌 (이름 + 짧은 설명)
    GildamTooltip(label, detail = metric.descKo) {
        Surface(
            shape = RoundedCornerShape(50),
            color = cs.surface.copy(alpha = 0.96f),
            border = BorderStroke(1.5.dp, cs.primary.copy(alpha = 0.6f)),
            shadowElevation = 6.dp,
            modifier = modifier.semantics { contentDescription = spoken }
        ) {
            Row(
                modifier = Modifier.padding(start = 6.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(brandGradient()),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(metric.icon, contentDescription = null, tint = onBrand(), modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = metric.labelWithUnit,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.5.sp,
                        color = cs.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = value,
                        style = TabularStatMediumStyle.copy(fontSize = 17.sp, lineHeight = 21.sp),
                        color = cs.onSurface
                    )
                }
            }
        }
    }
}
