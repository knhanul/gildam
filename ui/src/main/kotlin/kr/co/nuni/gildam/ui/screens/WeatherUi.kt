package kr.co.nuni.gildam.ui.screens

// ... (existing imports and earlier functions remain unchanged)

/**
 * 기록 중 계기판 날씨 칸의 바람 표시.
 * 화살표 높이를 텍스트 2단 높이에 맞추고, 왼쪽에 배치합니다.
 */
@Composable
internal fun GaugeWindDisplay(
    windFromDeg: Double?,
    windSpeedMs: Double?,
    mapBearingDeg: Float,
    headingUp: Boolean,
    modifier: Modifier = Modifier
) {
    if (windFromDeg == null && windSpeedMs == null) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        windFromDeg?.let { dir ->
            WindDial(
                windFromDeg = dir,
                mapBearingDeg = mapBearingDeg,
                headingUp = headingUp,
                size = 40.dp   // 2단 텍스트 높이에 맞춤
            )
            Spacer(Modifier.width(8.dp))
        }
        Column {
            windSpeedMs?.let { speed ->
                Text(
                    text = stringResource(R.string.weather_wind_ms, formatWeatherNumber(speed)),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
            windFromDeg?.let { dir ->
                val (relText, relColor) = windSideText(dir, headingUp, mapBearingDeg)
                Text(
                    text = relText,
                    style = MaterialTheme.typography.labelMedium,
                    color = relColor,
                    maxLines = 1
                )
            }
        }
    }
}

// Note: Replace the existing small WindDial + inline texts in the live gauge weather cell
// with a call to GaugeWindDisplay(...). The rest of WeatherUi.kt is unchanged.
