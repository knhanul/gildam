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
        deviceConfig = DeviceConfig.PIXEL_6.copy(locale = "ko"),
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

    private val eastWest = (0 until 50).map { i -> LatLonPoint(37.5665 + 0.00006 * kotlin.math.sin(i / 5.0), 126.9780 + 0.00025 * i) }

    private val southward = (0 until 50).map { i -> LatLonPoint(37.5665 - 0.0002 * i, 126.9780 + 0.00004 * kotlin.math.sin(i / 6.0)) }

    @Test fun overviewSouthwardStartAtTop() = paparazzi.snapshot {
        Screen(MapCameraMode.OVERVIEW, live.copy(liveTrackPoints = southward, currentLocation = southward.last()))
    }

    @Test fun overviewEastWestRotated() = paparazzi.snapshot {
        Screen(MapCameraMode.OVERVIEW, live.copy(liveTrackPoints = eastWest, currentLocation = eastWest.last()))
    }

    private val followRoute: List<LatLonPoint> = track

    private fun followUi(
        name: String = "북악 능선 코스",
        remainingM: Double = 1_000.0,
        reversed: Boolean = false,
        offCourse: Boolean = false,
        offsetM: Double? = 4.0
    ) = CourseFollowUi(
        name = name,
        route = followRoute,
        totalM = 5_200.0,
        remainingM = remainingM,
        isOffCourse = offCourse,
        offsetM = offsetM,
        reversed = reversed
    )

    @Composable
    private fun Screen(
        mode: MapCameraMode,
        state: LiveRecordingState = live,
        tooltip: String? = null,
        issues: List<RecordingIssue> = emptyList(),
        follow: CourseFollowUi? = null
    ) {
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
                    follow = follow,
                    defaultCameraMode = mode,
                    overlayMetric = GaugeMetric.CURRENT_SPEED,
                    fixedClockMs = 1_700_000_000_000L,
                    healthIssues = issues
                )
            }
        }
    }

    @Test fun issueSingleShortText() = paparazzi.snapshot {
        Screen(MapCameraMode.HEADING, issues = listOf(RecordingIssue.BATTERY_OPTIMIZATION))
    }

    @Test fun issueDetailSheet() = paparazzi.snapshot {
        GildamTheme {
            androidx.compose.material3.Surface {
                RecordingIssuesDetail(
                    listOf(RecordingIssue.LOCATION_OFF, RecordingIssue.BACKGROUND_LOCATION, RecordingIssue.GPS_WEAK,
                        RecordingIssue.BATTERY_OPTIMIZATION, RecordingIssue.POWER_SAVER, RecordingIssue.NOTIFICATIONS_OFF),
                    onOpenSettings = {}
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

    @Test fun followCourseForward() = paparazzi.snapshot {
        Screen(MapCameraMode.HEADING, follow = followUi())
    }

    @Test fun followCourseReverse() = paparazzi.snapshot {
        Screen(
            MapCameraMode.HEADING,
            follow = followUi(name = "탄천 자전거길", remainingM = 3_400.0, reversed = true),
            issues = listOf(RecordingIssue.BACKGROUND_LOCATION)
        )
    }

    @Test fun followCourseLongName() = paparazzi.snapshot {
        Screen(
            MapCameraMode.HEADING,
            follow = followUi(name = "서울 도심에서 북한산 정상까지 이어지는 아주 긴 능선 코스", remainingM = 8_400.0)
        )
    }
}

/** 360dp 좁은 폰 */
class LiveRecordingMapNarrowPreviewTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(screenWidth = 1080, screenHeight = 2160, xdpi = 480, ydpi = 480, density = com.android.resources.Density.XXHIGH, locale = "ko"),
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false
    )

    @Test fun narrow360Overview() = narrow(emptyList())

    @Test fun narrow360MultipleIssues() = narrow(
        listOf(RecordingIssue.BACKGROUND_LOCATION, RecordingIssue.BATTERY_OPTIMIZATION, RecordingIssue.POWER_SAVER)
    )

    @Test fun narrow360FollowCourse() = narrow(
        listOf(RecordingIssue.BACKGROUND_LOCATION),
        follow = CourseFollowUi(
            name = "서울 도심에서 북한산 정상까지 이어지는 아주 긴 능선 코스",
            route = listOf(LatLonPoint(37.56, 126.97), LatLonPoint(37.565, 126.985), LatLonPoint(37.57, 126.99)),
            totalM = 5_200.0,
            remainingM = 1_000.0,
            isOffCourse = false,
            offsetM = 4.0,
            reversed = true
        )
    )

    private fun narrow(issues: List<RecordingIssue>, follow: CourseFollowUi? = null) = paparazzi.snapshot {
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
                follow = follow,
                defaultCameraMode = MapCameraMode.OVERVIEW,
                overlayMetric = GaugeMetric.CLOCK,
                fixedClockMs = 1_700_000_000_000L,
                healthIssues = issues
            )
                } }
    }
}
