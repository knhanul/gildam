package kr.co.nuni.gildam.ui.screens

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

@Composable
fun StartWeatherCard(
    weather: WeatherNow,
    transportType: TransportType,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        modifier = modifier.fillMaxWidth()
    ) {
        if (transportType == TransportType.BIKE) {
            BikeWindContent(weather, compact = true, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
        } else {
            CompactWeatherRow(weather, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
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
    Icon(
        imageVector = Icons.Filled.Navigation,
        contentDescription = stringResource(R.string.weather_dir_arrow_cd),
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .size(size)
            .rotate(windTowardRotationDeg(dirDeg))
    )
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
    modifier: Modifier = Modifier
) {
    val contentAlpha = if (isStale) 0.48f else 1f
    val padV = if (compactVertical) 6.dp else 10.dp
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = padV),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.gauge_weather),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(if (compactVertical) 1.dp else 2.dp))
            if (weather == null || !weather.hasDisplayableData()) {
                Text(
                    text = stringResource(R.string.gauge_value_missing),
                    style = valueStyle.copy(textAlign = TextAlign.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            } else if (transportType == TransportType.BIKE) {
                BikeGaugeWeatherBody(
                    weather = weather,
                    valueStyle = valueStyle,
                    compactVertical = compactVertical,
                    modifier = Modifier.alpha(contentAlpha)
                )
            } else {
                GeneralGaugeWeatherBody(
                    weather = weather,
                    valueStyle = valueStyle,
                    compactVertical = compactVertical,
                    modifier = Modifier.alpha(contentAlpha)
                )
            }
        }
    }
}

@Composable
private fun BikeGaugeWeatherBody(
    weather: WeatherNow,
    valueStyle: TextStyle,
    compactVertical: Boolean,
    modifier: Modifier = Modifier
) {
    val speed = weather.windSpeedMs
    val dir = weather.windDirDeg
    val skyIcon = weatherConditionIcon(weather)
    val tempText = weather.tempC?.let { stringResource(R.string.weather_temp, formatWeatherNumber(it)) }
    val heroSp = if (compactVertical) 20.sp else 22.sp
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (dir != null) {
                WindDirectionArrow(dir, size = if (compactVertical) 17.dp else 19.dp)
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = if (speed != null) {
                    stringResource(R.string.weather_wind_ms, formatWeatherNumber(speed))
                } else {
                    tempText ?: stringResource(R.string.gauge_value_missing)
                },
                style = valueStyle.copy(
                    fontSize = heroSp,
                    lineHeight = (heroSp.value + 4f).sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
        if (speed != null && (tempText != null || skyIcon != null)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                skyIcon?.let { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(R.string.weather_sky_icon_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                }
                tempText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneralGaugeWeatherBody(
    weather: WeatherNow,
    valueStyle: TextStyle,
    compactVertical: Boolean,
    modifier: Modifier = Modifier
) {
    val skyIcon = weatherConditionIcon(weather)
    val tempText = weather.tempC?.let { stringResource(R.string.weather_temp, formatWeatherNumber(it)) }
    val speed = weather.windSpeedMs
    val dir = weather.windDirDeg
    val heroSp = if (compactVertical) 20.sp else 22.sp
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            skyIcon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = stringResource(R.string.weather_sky_icon_cd),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (compactVertical) 16.dp else 18.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = tempText ?: speed?.let { stringResource(R.string.weather_wind_ms, formatWeatherNumber(it)) }
                    ?: stringResource(R.string.gauge_value_missing),
                style = valueStyle.copy(
                    fontSize = heroSp,
                    lineHeight = (heroSp.value + 4f).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
        }
        if (tempText != null && (dir != null || speed != null)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                dir?.let { WindDirectionArrow(it, size = 11.dp) }
                speed?.let {
                    Text(
                        text = stringResource(R.string.weather_wind_ms, formatWeatherNumber(it)),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
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
