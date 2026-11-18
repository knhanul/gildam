package kr.co.nuni.gildam.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import kr.co.nuni.gildam.data.model.GaugeMetric
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.data.weather.PrecipType
import kr.co.nuni.gildam.data.weather.SkyCondition
import kr.co.nuni.gildam.data.weather.WeatherNow
import kr.co.nuni.gildam.data.weather.WeatherSource
import kr.co.nuni.gildam.map.MapCameraMode
import kr.co.nuni.gildam.recording.LatLonPoint
import kr.co.nuni.gildam.recording.LiveRecordingState
import kr.co.nuni.gildam.recording.LiveWeatherState
import kr.co.nuni.gildam.ui.theme.GildamTheme
import org.junit.Rule
import org.junit.Test

private const val FIXED_CLOCK_MS = 1_731_628_800_000L

private val sampleTrack: List<LatLonPoint> = buildList {
    var lat = 37.5665
    var lon = 126.9780
    repeat(40) { i ->
        lat += 0.00014
        lon += 0.00016 * (if (i < 20) 1.0 else 0.5)
        add(LatLonPoint(lat, lon))
    }
}

private val clearWalkWeather = WeatherNow(
    source = WeatherSource.OPEN_METEO,
    tempC = 18.0,
    precipType = PrecipType.NONE,
    windSpeedMs = 3.2,
    windDirDeg = 315.0,
    sky = SkyCondition.CLEAR
)

private val windyBikeWeather = WeatherNow(
    source = WeatherSource.OPEN_METEO,
    tempC = 16.5,
    precipType = PrecipType.NONE,
    windSpeedMs = 6.4,
    windDirDeg = 270.0,
    sky = SkyCondition.CLOUDY
)

/** 기록 중 2×3 계기판(오르막·내리막 + 실시간 날씨): 일반 활동 · 자전거 · 날씨 없음/오래된 값 */
class LiveGaugeElevationWeatherPreviewTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(locale = "ko"),
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false
    )

    @Test
    fun liveHikeWithWeatherAndElevation() = paparazzi.snapshot {
        LiveScreen(
            type = TransportType.HIKE,
            overlay = GaugeMetric.DISTANCE,
            weather = LiveWeatherState(weather = clearWalkWeather, fetchedAtEpochMs = FIXED_CLOCK_MS - 5 * 60_000L),
            gainM = 125f,
            lossM = 98f
        )
    }

    @Test
    fun liveBikeWindEmphasized() = paparazzi.snapshot {
        LiveScreen(
            type = TransportType.BIKE,
            overlay = GaugeMetric.CURRENT_SPEED,
            weather = LiveWeatherState(weather = windyBikeWeather, fetchedAtEpochMs = FIXED_CLOCK_MS - 10 * 60_000L),
            gainM = 84f,
            lossM = 76f
        )
    }

    @Test
    fun liveWeatherMissing() = paparazzi.snapshot {
        LiveScreen(
            type = TransportType.WALK,
            overlay = GaugeMetric.TOTAL_TIME,
            weather = LiveWeatherState(),
            gainM = 32f,
            lossM = 29f
        )
    }

    @Test
    fun liveWeatherStaleDimmed() = paparazzi.snapshot {
        LiveScreen(
            type = TransportType.BIKE,
            overlay = GaugeMetric.CURRENT_SPEED,
            weather = LiveWeatherState(weather = windyBikeWeather, fetchedAtEpochMs = FIXED_CLOCK_MS - 90 * 60_000L),
            gainM = 210f,
            lossM = 195f
        )
    }
}

/** 작은 화면(360 × 640dp, XXHIGH 1080×1920 @ 480dpi)에서도 지도가 너무 줄어들지 않고 2×3 계기판이 들어가는지 확인 */
class LiveGaugeSmall640PreviewTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(
            screenWidth = 1080,
            screenHeight = 1920,
            xdpi = 480,
            ydpi = 480,
            density = Density.XXHIGH,
            locale = "ko"
        ),
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false
    )

    @Test
    fun small640LiveBike() = paparazzi.snapshot {
        LiveScreen(
            type = TransportType.BIKE,
            overlay = GaugeMetric.CURRENT_SPEED,
            weather = LiveWeatherState(weather = windyBikeWeather, fetchedAtEpochMs = FIXED_CLOCK_MS - 10 * 60_000L),
            gainM = 125f,
            lossM = 98f
        )
    }

    @Test
    fun small640LiveWalkNoWeather() = paparazzi.snapshot {
        LiveScreen(
            type = TransportType.WALK,
            overlay = GaugeMetric.TOTAL_TIME,
            weather = LiveWeatherState(),
            gainM = 48f,
            lossM = 45f
        )
    }
}

@Composable
private fun LiveScreen(
    type: TransportType,
    overlay: GaugeMetric,
    weather: LiveWeatherState,
    gainM: Float,
    lossM: Float
) {
    val state = LiveRecordingState(
        isRecording = true,
        transportType = type,
        elapsedTotalMs = (42 * 60 + 15) * 1000L,
        elapsedMovingMs = (39 * 60) * 1000L,
        distanceMeters = 9_420.0,
        currentSpeedMps = if (type == TransportType.BIKE) 5.8f else 1.4f,
        movingAvgSpeedMps = if (type == TransportType.BIKE) 5.2f else 1.3f,
        elevationGainM = gainM,
        elevationLossM = lossM,
        liveWeather = weather,
        currentBearingDeg = 35f,
        currentLocation = sampleTrack.last(),
        liveTrackPoints = sampleTrack
    )
    CompositionLocalProvider(LocalInspectionMode provides true) {
        GildamTheme {
            LiveRecordingScreen(
                liveState = state,
                selectedTransport = type,
                onSelectTransport = {},
                onStartRecording = {},
                onPauseRecording = {},
                onResumeRecording = {},
                onStopRecording = {},
                onAddWaypoint = { _, _, _ -> },
                onNavigateToList = {},
                onNavigateToSettings = {},
                showTopNavIcons = false,
                defaultCameraMode = MapCameraMode.HEADING,
                overlayMetric = overlay,
                fixedClockMs = FIXED_CLOCK_MS,
                healthIssues = emptyList()
            )
        }
    }
}
