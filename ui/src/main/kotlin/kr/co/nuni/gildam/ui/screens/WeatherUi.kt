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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.data.weather.PrecipType
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
private fun WindDirectionArrow(dirDeg: Double, size: androidx.compose.ui.unit.Dp) {
    Icon(
        imageVector = Icons.Filled.Navigation,
        contentDescription = stringResource(R.string.weather_dir_arrow_cd),
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .size(size)
            .rotate(windTowardRotationDeg(dirDeg))
    )
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
