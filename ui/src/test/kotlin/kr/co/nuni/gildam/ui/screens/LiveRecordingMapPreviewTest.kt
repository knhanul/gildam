package kr.co.nuni.gildam.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import kr.co.nuni.gildam.data.model.GaugeMetric
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.map.MapCameraMode
import kr.co.nuni.gildam.recording.LatLonPoint
import kr.co.nuni.gildam.recording.LiveRecordingState
import kr.co.nuni.gildam.ui.illustration.LocalTooltipPreviewLabel
import kr.co.nuni.gildam.ui.theme.GildamTheme
import org.junit.Rule
import org.junit.Test

/** 기록 중 지도 화면: 전체 경로 / 현재 경로 / 북쪽 위 / 오류 상태 / 좁은 폰 */
class LiveRecordingMapPreviewTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false
    )

    private val track: List<LatLonPoint> = buildList {
        var lat = 37.5665; var lon = 126.9780
        repeat(60) { i ->
            lat += 0.00012 + (if (i % 10 < 5) 0.00004 else -0.00002)
            lon += 0.00018 * (if (i < 30) 1.0 else 0.4)
            add(LatLonPoint(lat, lon))
        }
    }

    private val live = LiveRecordingState(
        isRecording = true,
        elapsedTotalMs = (35 * 60 + 32) * 1000L,
        elapsedMovingMs = (33 * 60) * 1000L,
        distanceMeters = 12_345.0,
        currentSpeedMps = 5.1f,
        movingAvgSpeedMps = 4.6f,
        currentBearingDeg = 40f,
        currentLocation = track.last(),
        liveTrackPoints = track
    )

    @Composable
    private fun Screen(mode: MapCameraMode, state: LiveRecordingState = live, tooltip: String? = null) {
        CompositionLocalProvider(LocalTooltipPreviewLabel provides tooltip, androidx.compose.ui.platform.LocalInspectionMode provides true) {
            GildamTheme {
                LiveRecordingScreen(
                    liveState = state,
                    selectedTransport = TransportType.BIKE,
                    onSelectTransport = {},
                    onStartRecording = {}, onPauseRecording = {}, onResumeRecording = {}, onStopRecording = {},
                    onAddWaypoint = { _, _, _ -> },
                    onNavigateToList = {}, onNavigateToSettings = {},
                    showTopNavIcons = false,
                    defaultCameraMode = mode,
                    overlayMetric = GaugeMetric.CURRENT_SPEED,
                    fixedClockMs = 1_700_000_000_000L
                )
            }
        }
    }

    @Test fun overview() = paparazzi.snapshot { Screen(MapCameraMode.OVERVIEW) }

    @Test fun currentRouteHeadingUp() = paparazzi.snapshot { Screen(MapCameraMode.HEADING) }

    @Test fun currentRouteNorthUp() = paparazzi.snapshot { Screen(MapCameraMode.FOLLOW) }

    @Test fun gpsWeakError() = paparazzi.snapshot {
        Screen(MapCameraMode.HEADING, live.copy(isGpsSignalWeak = true), tooltip = RecordingStatus.GPS_WEAK.title)
    }
}

/** 360dp 좁은 폰 */
class LiveRecordingMapNarrowPreviewTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(screenWidth = 1080, screenHeight = 2160, xdpi = 480, ydpi = 480, density = com.android.resources.Density.XXHIGH),
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false
    )

    @Test fun narrow360Overview() = paparazzi.snapshot {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) { GildamTheme {
            LiveRecordingScreen(
                liveState = LiveRecordingState(
                    isRecording = true,
                    elapsedTotalMs = (35 * 60 + 32) * 1000L,
                    distanceMeters = 123_456.0,
                    currentSpeedMps = 30f,
                    movingAvgSpeedMps = 27.9f,
                    currentLocation = LatLonPoint(37.57, 126.99),
                    liveTrackPoints = listOf(LatLonPoint(37.56, 126.97), LatLonPoint(37.565, 126.985), LatLonPoint(37.57, 126.99))
                ),
                selectedTransport = TransportType.CAR,
                onSelectTransport = {},
                onStartRecording = {}, onPauseRecording = {}, onResumeRecording = {}, onStopRecording = {},
                onAddWaypoint = { _, _, _ -> },
                onNavigateToList = {}, onNavigateToSettings = {},
                showTopNavIcons = false,
                defaultCameraMode = MapCameraMode.OVERVIEW,
                overlayMetric = GaugeMetric.CLOCK,
                fixedClockMs = 1_700_000_000_000L
            )
                } }
    }
}
