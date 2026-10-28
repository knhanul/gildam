package kr.co.nuni.gildam.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import kr.co.nuni.gildam.ui.theme.TabularStatHeroStyle

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
 * 지도 위(지도 하단)에 겹쳐 보이는 계기판 칩.
 * - 글자 없이 **숫자와 단위만** 크게 보여 줍니다. 이름은 길게 눌렀을 때 말풍선으로만 뜹니다.
 * - 밝은 지도·어두운 지도 어디에서도 보이도록 테마 표면색 + 테마 기본색 테두리 + 그림자를 함께 씁니다.
 * - 터치 영역은 48dp 이상이라 말풍선을 띄우기 쉽습니다.
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
            border = BorderStroke(1.5.dp, cs.primary.copy(alpha = 0.55f)),
            shadowElevation = 8.dp,
            modifier = modifier.semantics { contentDescription = spoken }
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 20.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(brandGradient()),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(metric.icon, contentDescription = null, tint = onBrand(), modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = value,
                    style = TabularStatHeroStyle.copy(fontSize = 32.sp, lineHeight = 36.sp),
                    color = cs.onSurface
                )
                metric.unit?.let { unit ->
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Bottom).padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}
