package kr.co.nuni.gildam.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import kr.co.nuni.gildam.data.weather.feelsLikeC
import kr.co.nuni.gildam.data.weather.relativeWind
import kr.co.nuni.gildam.data.weather.windArrowScreenDeg
import kr.co.nuni.gildam.data.weather.windCardinal16
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.data.weather.PrecipType
import kr.co.nuni.gildam.data.weather.SkyCondition
import kr.co.nuni.gildam.data.weather.WeatherNow
import kr.co.nuni.gildam.data.weather.windCardinal
import kr.co.nuni.gildam.data.weather.windStrength
import kr.co.nuni.gildam.data.weather.windTowardRotationDeg
import kr.co.nuni.gildam.ui.R
import kr.co.nuni.gildam.ui.l10n.label
import java.util.Locale
import kotlin.math.roundToInt

sealed class StartWeatherUi {
    data object Hidden : StartWeatherUi()
    data object NeedsLogin : StartWeatherUi()
    data object Unavailable : StartWeatherUi()
    data class Ready(val weather: WeatherNow) : StartWeatherUi()
}

/** 출발 화면(활동 선택 카드)에 넣는 작은 날씨 줄. 자전거는 바람을 크게 보여 줍니다. */
@Composable
fun StartWeatherSection(
    state: StartWeatherUi,
    transportType: TransportType,
    modifier: Modifier = Modifier
) {
    when (state) {
        StartWeatherUi.Hidden -> Unit
        StartWeatherUi.NeedsLogin -> {
            Text(
                text = stringResource(R.string.weather_needs_login),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }
        StartWeatherUi.Unavailable -> {
            Text(
                text = stringResource(R.string.weather_unavailable),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }
        is StartWeatherUi.Ready -> {
            StartWeatherCard(state.weather, transportType, modifier.padding(bottom = 12.dp))
        }
    }
}

/**
 * 출발 전 날씨: 높이를 줄인 한 줄. 두 줄로 넘어가지 않습니다.
 * 순서: 하늘·강수 아이콘 · 기온 · 체감 · 바람(화살표·m/s·방향) · 습도 · 강수량.
 * 자전거는 바람을 맨 앞에 굵게. 폭이 좁으면 습도 → 강수량 → 체감 → 아이콘 순으로 뺍니다.
 */
@Composable
fun StartWeatherCard(
    weather: WeatherNow,
    transportType: TransportType,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        modifier = modifier.fillMaxWidth()
    ) {
        StartWeatherRow(
            weather = weather,
            bikeFirst = transportType == TransportType.BIKE,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

/** [StartWeatherCard] 안의 한 줄 (미리보기·테스트에서도 씀) */
@Composable
internal fun StartWeatherRow(weather: WeatherNow, bikeFirst: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val small = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
    val items = mutableListOf<Pair<Int, @Composable () -> Unit>>()
    // 바람 (자전거면 맨 앞·굵게)
    val wind: (@Composable () -> Unit)? = if (weather.windSpeedMs != null || weather.windDirDeg != null) {
        {
            Row(verticalAlignment = Alignment.CenterVertically) {
                weather.windDirDeg?.let { dir ->
                    WindArrowGlyph(
                        rotationDeg = windTowardRotationDeg(dir),
                        color = if (bikeFirst) cs.primary else cs.onSurfaceVariant,
                        size = if (bikeFirst) 18.dp else 15.dp
                    )
                    Spacer(Modifier.width(3.dp))
                }
                val parts = listOfNotNull(
                    weather.windSpeedMs?.let { stringResource(R.string.weather_wind_ms, formatWeatherNumber(it)) },
                    weather.windDirDeg?.let { windCardinal(it).label }
                ).joinToString(" ")
                Text(
                    parts,
                    style = if (bikeFirst) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
                    else small,
                    color = cs.onSurface,
                    maxLines = 1, softWrap = false
                )
            }
        }
    } else null
    if (bikeFirst && wind != null) items += 0 to wind
    val icon = weatherConditionIcon(weather) ?: Icons.Outlined.WbCloudy
    items += 4 to {
        Icon(icon, contentDescription = conditionLabel(weather) ?: stringResource(R.string.weather_sky_icon_cd),
            tint = cs.primary, modifier = Modifier.size(18.dp))
    }
    weather.tempC?.let { t ->
        items += 0 to {
            Text(
                stringResource(R.string.weather_temp, formatWeatherNumber(t)),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (bikeFirst) FontWeight.Medium else FontWeight.Bold, fontFeatureSettings = "tnum"
                ),
                color = cs.onSurface, maxLines = 1, softWrap = false
            )
        }
        items += 3 to {
            Text(
                stringResource(R.string.weather_feels_like, formatWeatherNumber(feelsLikeC(t, weather.windSpeedMs, weather.humidity))),
                style = small, color = cs.onSurfaceVariant, maxLines = 1, softWrap = false
            )
        }
    }
    if (!bikeFirst && wind != null) items += 0 to wind
    weather.humidity?.let { h ->
        items += 1 to {
            Text(stringResource(R.string.weather_humidity, h.roundToInt().toString()),
                style = small, color = cs.onSurfaceVariant, maxLines = 1, softWrap = false)
        }
    }
    items += 2 to {
        val mm = weather.precip1hMm
        val text = when {
            weather.precipType != PrecipType.NONE && mm != null ->
                stringResource(R.string.weather_precip_type_mm, weather.precipType.label, formatWeatherNumber(mm))
            weather.precipType != PrecipType.NONE -> weather.precipType.label
            mm != null -> stringResource(R.string.weather_precip_mm, formatWeatherNumber(mm))
            else -> stringResource(R.string.weather_precip_none)
        }
        Text(text, style = small, color = cs.onSurfaceVariant, maxLines = 1, softWrap = false)
    }
    OneLineDroppingRow(items = items, minGap = 10.dp, modifier = modifier.fillMaxWidth())
}

/**
 * 한 줄에 다 안 들어가면 순위(1부터, 작은 수부터) 낮은 칸을 빼서 언제나 한 줄로 둡니다.
 * 순위 0 은 빼지 않습니다. 남는 폭은 칸 사이에 고르게 나눕니다.
 */
@Composable
internal fun OneLineDroppingRow(
    items: List<Pair<Int, @Composable () -> Unit>>,
    minGap: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Layout(content = { items.forEach { it.second() } }, modifier = modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val gap = minGap.roundToPx()
        val visible = BooleanArray(placeables.size) { true }
        fun need(): Int {
            val shown = placeables.indices.filter { visible[it] }
            return shown.sumOf { placeables[it].width } + gap * (shown.size - 1).coerceAtLeast(0)
        }
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else need()
        items.indices.filter { items[it].first > 0 }.sortedBy { items[it].first }.forEach { i ->
            if (need() > maxW) visible[i] = false
        }
        val shown = placeables.indices.filter { visible[it] }
        val h = shown.maxOfOrNull { placeables[it].height } ?: 0
        val used = shown.sumOf { placeables[it].width }
        val spread = if (shown.size > 1) ((maxW - used) / (shown.size - 1)).coerceAtLeast(gap) else 0
        layout(maxW, h.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            var x = 0
            shown.forEach { i ->
                val p = placeables[i]
                p.place(x, (h - p.height) / 2)
                x += p.width + spread
            }
        }
    }
}

/** 기록 상세에 넣는 출발 시점 날씨. 자전거는 바람을 앞에 둡니다. */
@Composable
fun RecordWeatherCard(
    weather: WeatherNow,
    transportType: TransportType,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.weather_at_start),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (transportType == TransportType.BIKE) {
                BikeWindContent(weather, compact = false)
            } else {
                CompactWeatherRow(weather)
            }
        }
    }
}

@Composable
private fun CompactWeatherRow(weather: WeatherNow, modifier: Modifier = Modifier) {
    val parts = weatherSummaryParts(weather)
    if (parts.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        weather.windDirDeg?.let { WindDirectionArrow(it, size = 18.dp) }
        Text(
            text = parts.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BikeWindContent(
    weather: WeatherNow,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val speed = weather.windSpeedMs
    val dir = weather.windDirDeg
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dir != null) {
                WindDirectionArrow(dir, size = if (compact) 28.dp else 32.dp)
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                if (speed != null) {
                    Text(
                        text = stringResource(R.string.weather_wind_ms, formatWeatherNumber(speed)),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = if (compact) 24.sp else 28.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                val dirLabel = dir?.let { windCardinal(it).label }
                val strength = speed?.let { windStrength(it).label }
                val mid = listOfNotNull(dirLabel, strength).joinToString(" · ")
                if (mid.isNotEmpty()) {
                    Text(
                        text = mid,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        weather.windGustMs?.let { gust ->
            Text(
                text = stringResource(R.string.weather_gust, formatWeatherNumber(gust)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        val secondary = secondarySummaryParts(weather)
        if (secondary.isNotEmpty()) {
            Text(
                text = secondary.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
internal fun WindDirectionArrow(dirDeg: Double, size: androidx.compose.ui.unit.Dp) {
    WindArrowGlyph(
        rotationDeg = windTowardRotationDeg(dirDeg),
        color = MaterialTheme.colorScheme.primary,
        size = size,
        contentDescription = stringResource(R.string.weather_dir_arrow_cd)
    )
}

/**
 * 꼬리가 달린 굵은 화살표 (위를 가리키는 모양을 [rotationDeg] 만큼 돌림).
 * 삼각형보다 방향이 한눈에 읽힙니다.
 */
@Composable
internal fun WindArrowGlyph(
    rotationDeg: Float,
    color: Color,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Canvas(
        modifier
            .size(size)
            .rotate(rotationDeg)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
    ) {
        val w = this.size.width
        val h = this.size.height
        val shaft = w * 0.2f
        val headBase = h * 0.48f
        val path = Path().apply {
            moveTo(w / 2f, h * 0.04f)            // 머리 끝
            lineTo(w * 0.9f, headBase)
            lineTo(w / 2f + shaft / 2f, headBase)
            lineTo(w / 2f + shaft / 2f, h * 0.96f) // 꼬리
            lineTo(w / 2f - shaft / 2f, h * 0.96f)
            lineTo(w / 2f - shaft / 2f, headBase)
            lineTo(w * 0.1f, headBase)
            close()
        }
        drawPath(path, color)
        drawPath(path, color, style = Stroke(width = w * 0.06f, join = StrokeJoin.Round))
    }
}

/** 진행 방향 위 모드의 바람 색 (주황) */
internal val WindHeadingOrange = Color(0xFFF57C00)

/**
 * 기록 중 바람 표시. 두 모드가 확실히 달라 보이게:
 * - 진행 방향 위([headingUp]): 지도가 돈 만큼 같이 도는 주황 화살표 + 연한 주황 동그라미
 * - 북쪽 위: 실제 방위 그대로인 회색 화살표 + 테두리 원과 작은 'N'
 */
@Composable
internal fun WindDial(
    windFromDeg: Double,
    mapBearingDeg: Float,
    headingUp: Boolean,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val rot = windArrowScreenDeg(windFromDeg, mapBearingDeg.toDouble())
    if (headingUp) {
        val cd = stringResource(R.string.wind_mode_heading_cd)
        Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .background(WindHeadingOrange.copy(alpha = 0.16f))
                .semantics { contentDescription = cd },
            contentAlignment = Alignment.Center
        ) {
            WindArrowGlyph(rot, WindHeadingOrange, size * 0.8f)
        }
    } else {
        val gray = MaterialTheme.colorScheme.onSurfaceVariant
        val cd = stringResource(R.string.wind_mode_north_cd)
        Box(modifier.size(size).semantics { contentDescription = cd }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                drawCircle(gray.copy(alpha = 0.55f), radius = this.size.minDimension / 2f - 0.6.dp.toPx(), style = Stroke(1.dp.toPx()))
            }
            // 'N' 표시: 지도가 돌아가 있으면 실제 북쪽으로 (북쪽 위면 늘 맨 위)
            Box(Modifier.size(size).rotate(-mapBearingDeg), contentAlignment = Alignment.TopCenter) {
                Text(
                    stringResource(R.string.weather_north_marker),
                    fontSize = (size.value * 0.30f).coerceAtLeast(6f).sp,
                    lineHeight = (size.value * 0.30f).coerceAtLeast(6f).sp,
                    fontWeight = FontWeight.Bold,
                    color = gray,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 1.dp)
                )
            }
            WindArrowGlyph(rot, gray, size * 0.58f)
        }
    }
}

internal fun weatherConditionIcon(weather: WeatherNow): ImageVector? = when {
    weather.precipType == PrecipType.RAIN -> Icons.Outlined.Umbrella
    weather.precipType == PrecipType.SNOW -> Icons.Outlined.AcUnit
    weather.precipType == PrecipType.SLEET -> Icons.Outlined.Grain
    weather.sky == SkyCondition.CLEAR -> Icons.Outlined.WbSunny
    weather.sky == SkyCondition.CLOUDY -> Icons.Outlined.CloudQueue
    weather.sky == SkyCondition.OVERCAST -> Icons.Outlined.Cloud
    else -> null
}

/**
 * 기록 중 계기판 오른쪽 아래 '날씨' 칸:
 * - 일반 활동: 기온(크게) + 하늘 상태 아이콘 + 바람(방향 화살표, m/s)
 * - 자전거: 바람(방향 화살표 + 풍속 m/s)을 가장 크게 강조하고 기온·하늘 아이콘은 아래에 작게 표시 (출발 시트 바람 강조 스타일 재사용)
 * - 값이 없으면 '–', 60분이 넘은 오래된 값은 흐리게(alpha 0.48) 표시합니다.
 */
@Composable
internal fun LiveWeatherGaugeCard(
    weather: WeatherNow?,
    isStale: Boolean,
    transportType: TransportType,
    valueStyle: TextStyle,
    compactVertical: Boolean,
    modifier: Modifier = Modifier,
    /** 지도가 진행 방향 위인지 (아니면 북쪽 위·전체 보기) */
    headingUp: Boolean = false,
    /** 지금 지도가 돌아간 각도 (북쪽 위면 0) */
    mapBearingDeg: Float = 0f
) {
    val contentAlpha = if (isStale) 0.48f else 1f
    val padV = if (compactVertical) 6.dp else 10.dp
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        val dir = weather?.takeIf { it.hasDisplayableData() }?.windDirDeg
        val titleGap = if (compactVertical) 1.dp else 2.dp
        if (dir == null) {
            // 바람 방향이 없으면 예전처럼 제목·값을 가운데에
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = padV),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WeatherGaugeTitle()
                Spacer(modifier = Modifier.height(titleGap))
                LiveWeatherGaugeValues(weather, transportType, headingUp, mapBearingDeg, valueStyle, compactVertical, contentAlpha)
            }
        } else {
            // 바람 화살표를 왼쪽에 '날씨' 제목까지 포함한 3줄 높이로, 오른쪽 글자와는 한 글자쯤 띄움
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = padV),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 제목(16sp) + 큰 값(24~26sp) + 작은 줄(16sp) 높이에 맞춤
                WindDial(dir, mapBearingDeg, headingUp, size = if (compactVertical) 52.dp else 58.dp,
                    modifier = Modifier.alpha(contentAlpha))
                Spacer(Modifier.width(14.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    WeatherGaugeTitle()
                    Spacer(modifier = Modifier.height(titleGap))
                    LiveWeatherGaugeValues(weather, transportType, headingUp, mapBearingDeg, valueStyle, compactVertical, contentAlpha)
                }
            }
        }
    }
}

@Composable
private fun WeatherGaugeTitle() {
    Text(
        text = stringResource(R.string.gauge_weather),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
    )
}

/** 날씨 칸의 값 2줄 (바람 화살표 제외) */
@Composable
private fun LiveWeatherGaugeValues(
    weather: WeatherNow?,
    transportType: TransportType,
    headingUp: Boolean,
    mapBearingDeg: Float,
    valueStyle: TextStyle,
    compactVertical: Boolean,
    contentAlpha: Float
) {
    if (weather == null || !weather.hasDisplayableData()) {
        Text(
            text = stringResource(R.string.gauge_value_missing),
            style = valueStyle.copy(textAlign = TextAlign.Center),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    } else if (transportType == TransportType.BIKE) {
        BikeGaugeWeatherBody(weather, headingUp, mapBearingDeg, valueStyle, compactVertical, Modifier.alpha(contentAlpha))
    } else {
        GeneralGaugeWeatherBody(weather, headingUp, mapBearingDeg, valueStyle, compactVertical, Modifier.alpha(contentAlpha))
    }
}

/** 바람 방향 글자: 진행 방향 위면 맞바람/뒷바람/옆바람(주황), 북쪽 위면 16방위 이름(회색) */
@Composable
private fun windSideText(dir: Double, headingUp: Boolean, mapBearingDeg: Float): Pair<String, Color> =
    if (headingUp) relativeWind(dir, mapBearingDeg.toDouble()).label to WindHeadingOrange
    else windCardinal16(dir).label to MaterialTheme.colorScheme.onSurfaceVariant

/** 날씨 칸 값 2줄: 큰 값 + 작은 설명. 바람 화살표는 [LiveWeatherGaugeCard] 가 왼쪽에 3줄 높이로 그림 */
@Composable
private fun WeatherValueColumn(
    centered: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier, horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        content()
    }
}

/** 자전거: 풍속(크게) / 맞바람·방위 · 기온 */
@Composable
private fun BikeGaugeWeatherBody(
    weather: WeatherNow,
    headingUp: Boolean,
    mapBearingDeg: Float,
    valueStyle: TextStyle,
    compactVertical: Boolean,
    modifier: Modifier = Modifier
) {
    val speed = weather.windSpeedMs
    val dir = weather.windDirDeg
    val tempText = weather.tempC?.let { stringResource(R.string.weather_temp, formatWeatherNumber(it)) }
    val heroSp = if (compactVertical) 20.sp else 22.sp
    WeatherValueColumn(centered = dir == null, modifier = modifier) {
        Text(
            text = speed?.let { stringResource(R.string.weather_wind_ms, formatWeatherNumber(it)) }
                ?: tempText ?: stringResource(R.string.gauge_value_missing),
            style = valueStyle.copy(fontSize = heroSp, lineHeight = (heroSp.value + 4f).sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Clip
        )
        val side = dir?.let { windSideText(it, headingUp, mapBearingDeg) }
        if (side != null || (speed != null && tempText != null)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                side?.let { (t, c) ->
                    Text(t, style = MaterialTheme.typography.labelSmall, fontWeight = if (headingUp) FontWeight.Bold else FontWeight.Medium,
                        color = c, maxLines = 1, softWrap = false)
                }
                if (speed != null) tempText?.let {
                    Text(if (side != null) "· $it" else it, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** 일반 활동: 하늘 아이콘·기온(크게) / 풍속 · 맞바람·방위 */
@Composable
private fun GeneralGaugeWeatherBody(
    weather: WeatherNow,
    headingUp: Boolean,
    mapBearingDeg: Float,
    valueStyle: TextStyle,
    compactVertical: Boolean,
    modifier: Modifier = Modifier
) {
    val skyIcon = weatherConditionIcon(weather)
    val tempText = weather.tempC?.let { stringResource(R.string.weather_temp, formatWeatherNumber(it)) }
    val speed = weather.windSpeedMs
    val dir = weather.windDirDeg
    val heroSp = if (compactVertical) 20.sp else 22.sp
    WeatherValueColumn(centered = dir == null, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            skyIcon?.let { icon ->
                Icon(icon, contentDescription = stringResource(R.string.weather_sky_icon_cd),
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(if (compactVertical) 16.dp else 18.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = tempText ?: speed?.let { stringResource(R.string.weather_wind_ms, formatWeatherNumber(it)) }
                    ?: stringResource(R.string.gauge_value_missing),
                style = valueStyle.copy(fontSize = heroSp, lineHeight = (heroSp.value + 4f).sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, softWrap = false
            )
        }
        val showSpeed = tempText != null && speed != null
        if (showSpeed || dir != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (showSpeed) speed?.let {
                    Text(stringResource(R.string.weather_wind_ms, formatWeatherNumber(it)),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                }
                dir?.let { d ->
                    val (t, c) = windSideText(d, headingUp, mapBearingDeg)
                    Text(if (showSpeed) "· $t" else t, style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (headingUp) FontWeight.Bold else FontWeight.Medium,
                        color = c, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun weatherSummaryParts(weather: WeatherNow): List<String> {
    val parts = mutableListOf<String>()
    weather.tempC?.let { parts += stringResource(R.string.weather_temp, formatWeatherNumber(it)) }
    conditionLabel(weather)?.let { parts += it }
    val wind = buildString {
        weather.windDirDeg?.let { append(windCardinal(it).label) }
        weather.windSpeedMs?.let { speed ->
            if (isNotEmpty()) append(" ")
            append(stringResource(R.string.weather_wind_ms, formatWeatherNumber(speed)))
        }
    }
    if (wind.isNotEmpty()) parts += wind
    return parts
}

@Composable
private fun secondarySummaryParts(weather: WeatherNow): List<String> {
    val parts = mutableListOf<String>()
    weather.tempC?.let { parts += stringResource(R.string.weather_temp, formatWeatherNumber(it)) }
    conditionLabel(weather)?.let { parts += it }
    return parts
}

@Composable
private fun conditionLabel(weather: WeatherNow): String? {
    val sky = weather.sky
    return when {
        weather.precipType != PrecipType.NONE -> weather.precipType.label
        sky != null -> sky.label
        else -> null
    }
}

internal fun formatWeatherNumber(value: Double, locale: Locale = Locale.getDefault()): String {
    val rounded = (value * 10.0).roundToInt() / 10.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        String.format(locale, "%.1f", rounded)
    }
}
