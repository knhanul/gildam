package kr.co.nuni.gildam.ui.screens

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.app.ActivityOptionsCompat
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import kr.co.nuni.gildam.data.local.entity.ActivityDetailBundle
import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.local.entity.TrackPointEntity
import kr.co.nuni.gildam.data.local.entity.WeatherSnapshotEntity
import kr.co.nuni.gildam.data.model.ActivityStatus
import kr.co.nuni.gildam.data.model.GaugeMetric
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.data.weather.PrecipType
import kr.co.nuni.gildam.data.weather.SkyCondition
import kr.co.nuni.gildam.data.weather.WeatherNow
import kr.co.nuni.gildam.data.weather.WeatherSource
import kr.co.nuni.gildam.map.MapCameraMode
import kr.co.nuni.gildam.recording.LatLonPoint
import kr.co.nuni.gildam.recording.LiveRecordingState
import kr.co.nuni.gildam.ui.theme.GildamTheme
import org.junit.Rule
import org.junit.Test

/** 출발 화면·기록 상세의 날씨 UI (가짜 데이터, 폰 크기) */
class WeatherPreviewTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(locale = "ko"),
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false
    )

    // KMA: windGustMs·sky 는 항상 null. 자전거 출발·상세는 이 경우에도 바람이 잘 보여야 함.
    private val bikeWeather = WeatherNow(
        source = WeatherSource.KMA,
        observedAtIso = "2026-11-15T02:00:00Z",
        tempC = 18.0,
        humidity = 55.0,
        precipType = PrecipType.NONE,
        precip1hMm = 0.0,
        windSpeedMs = 6.4,
        windDirDeg = 315.0,
        windGustMs = null,
        sky = null
    )

    // OPEN_METEO 는 sky·gust 를 채움. 걷기 출발은 기온을 앞에 두고 하늘 상태를 보여 줌.
    private val walkWeather = WeatherNow(
        source = WeatherSource.OPEN_METEO,
        observedAtIso = "2026-11-15T02:00:00Z",
        tempC = 18.0,
        humidity = 48.0,
        precipType = PrecipType.NONE,
        precip1hMm = 0.0,
        windSpeedMs = 3.2,
        windDirDeg = 315.0,
        windGustMs = 4.8,
        sky = SkyCondition.CLEAR
    )

    private val startState = LiveRecordingState(
        lastFinishedLocation = LatLonPoint(37.51, 126.89),
        lastFinishedTrackPoints = listOf(
            LatLonPoint(37.508, 126.888),
            LatLonPoint(37.51, 126.89)
        )
    )

    @Test
    fun startSheetBikeWindEmphasized() = paparazzi.snapshot {
        StartSheet(TransportType.BIKE, StartWeatherUi.Ready(bikeWeather))
    }

    @Test
    fun startSheetWalk() = paparazzi.snapshot {
        StartSheet(TransportType.WALK, StartWeatherUi.Ready(walkWeather))
    }

    @Test
    fun recordDetailWithWeather() = paparazzi.snapshot {
        val start = 1_731_628_800_000L
        val activity = ActivityEntity(
            id = "bike-weather",
            title = "한강 자전거",
            transportType = TransportType.BIKE,
            status = ActivityStatus.COMPLETED,
            startedAtEpochMs = start,
            endedAtEpochMs = start + 48 * 60_000L,
            totalDurationMs = 48 * 60_000L,
            movingDurationMs = 44 * 60_000L,
            distanceMeters = 12_400.0,
            movingAvgSpeedMps = 4.7f,
            elevationGainMeters = 28f
        )
        val points = (0 until 40).map { i ->
            TrackPointEntity(
                id = i.toLong() + 1,
                activityId = activity.id,
                seq = i,
                latitude = 37.51 + i * 0.00025,
                longitude = 126.89 + i * 0.0003,
                recordedAtEpochMs = start + i * 1_000L
            )
        }
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalActivityResultRegistryOwner provides previewActivityResultRegistryOwner()
        ) {
            GildamTheme {
                ActivityDetailScreen(
                    bundle = ActivityDetailBundle(
                        activity = activity,
                        waypoints = emptyList(),
                        weather = WeatherSnapshotEntity.from(activity.id, bikeWeather)
                    ),
                    trackPoints = points,
                    onVerifyPhotos = {},
                    onRelinkMissingPhoto = { _, _ -> },
                    onRemoveMissingPhotoKeepMemo = { _, _ -> },
                    onNavigateToSafeExport = {},
                    onNavigateBack = {}
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun StartSheet(type: TransportType, weather: StartWeatherUi) {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            GildamTheme {
                LiveRecordingScreen(
                    liveState = startState,
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
                    defaultCameraMode = MapCameraMode.FOLLOW,
                    overlayMetric = GaugeMetric.defaultFor(type),
                    fixedClockMs = 1_731_628_800_000L,
                    healthIssues = emptyList(),
                    startWeather = weather
                )
            }
        }
    }
}

private fun previewActivityResultRegistryOwner(): ActivityResultRegistryOwner =
    object : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry =
            object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?
                ) = Unit
            }
    }
