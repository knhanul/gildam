package kr.co.nuni.gildam.ui.screens

import kr.co.nuni.gildam.ui.l10n.*
import androidx.compose.ui.res.stringResource
import kr.co.nuni.gildam.ui.R
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
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
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
        GaugeMetric.NONE -> Icons.Outlined.VisibilityOff // 칩 자체는 표시하지 않음
    }

/** 말풍선에 쓰는 계기판 이름 (테스트에서 라벨로 찾음) */
@androidx.compose.runtime.Composable
fun GaugeMetric.tooltipLabel(): String = stringResource(R.string.gauge_tooltip, label)

/**
 * 지도 위(지도 하단)에 겹쳐 보이는 계기판 숫자.
 * - 배경·테두리 없이 **숫자와 단위만** 크게 보여 지도를 덜 가립니다. 이름은 길게 눌렀을 때 말풍선으로만 뜹니다.
 * - 밝은·어두운 지도 어디서나 읽히도록 흰 외곽선 + 옅은 그림자를 씁니다.
 */
@Composable
fun GaugeOverlayChip(
    metric: GaugeMetric,
    live: LiveRecordingState,
    clockTick: androidx.compose.runtime.State<Long>,
    modifier: Modifier = Modifier
) {
    val label = metric.tooltipLabel()
    val value = gaugeValue(metric, live, clockTick.value)
    val spoken = "${metric.label} $value${metric.unit?.let { " $it" } ?: ""}"
    GildamTooltip(label, detail = metric.desc) {
        Row(
            modifier = modifier
                .semantics { contentDescription = spoken }
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedNumber(value, TabularStatHeroStyle.copy(fontSize = 46.sp, lineHeight = 50.sp))
            metric.unit?.let { unit ->
                Spacer(Modifier.width(5.dp))
                OutlinedNumber(
                    unit,
                    TabularStatHeroStyle.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
                    strokeWidth = 5f,
                    modifier = Modifier.padding(bottom = 7.dp)
                )
            }
        }
    }
}

/** 진한 글자 + 흰 외곽선 + 옅은 그림자 (배경 없이 지도 위에서도 잘 보이게) */
@Composable
private fun OutlinedNumber(
    text: String,
    style: TextStyle,
    strokeWidth: Float = 9f,
    modifier: Modifier = Modifier
) {
    val ink = Color(0xFF16201B)
    Box(modifier) {
        Text(
            text = text,
            style = style.copy(
                color = Color.White,
                drawStyle = Stroke(width = strokeWidth, join = StrokeJoin.Round),
                shadow = Shadow(Color.Black.copy(alpha = 0.28f), Offset(0f, 2f), blurRadius = 6f)
            ),
            maxLines = 1
        )
        Text(text = text, style = style.copy(color = ink), maxLines = 1)
    }
}
