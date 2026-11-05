package kr.co.nuni.gildam.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import kr.co.nuni.gildam.auth.NuniSessionState
import kr.co.nuni.gildam.data.gpxcompare.RuggednessGrade
import kr.co.nuni.gildam.data.list.RecordGroupMode
import kr.co.nuni.gildam.data.list.RecordGrouper
import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.model.ActivityStatus
import kr.co.nuni.gildam.data.model.GaugeMetric
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.map.MapCameraMode
import kr.co.nuni.gildam.recording.LatLonPoint
import kr.co.nuni.gildam.recording.LiveRecordingState
import kr.co.nuni.gildam.sync.SyncUiStatus
import kr.co.nuni.gildam.ui.R
import kr.co.nuni.gildam.ui.l10n.displayTitle
import kr.co.nuni.gildam.ui.theme.GildamTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

private const val THEME = "android:Theme.Material.Light.NoActionBar"

/** 영어(기기 언어 en)로 주요 화면을 그려 글자가 넘치거나 겹치지 않는지 확인합니다. */
class EnglishPreviewTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(locale = "en"), theme = THEME, showSystemUi = false)

    private val track = (0 until 60).map { i ->
        LatLonPoint(37.5665 + 0.00012 * i + (if (i % 10 < 5) 0.00004 else -0.00002) * i, 126.9780 + 0.00018 * i * (if (i < 30) 1.0 else 0.4))
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
    private fun Live(mode: MapCameraMode, issues: List<RecordingIssue> = emptyList(), state: LiveRecordingState = live) {
        CompositionLocalProvider(LocalInspectionMode provides true) {
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
                    fixedClockMs = 1_700_000_000_000L,
                    healthIssues = issues
                )
            }
        }
    }

    @Test fun liveRecordingOverview() = paparazzi.snapshot { Live(MapCameraMode.OVERVIEW) }

    @Test fun liveRecordingIssues() = paparazzi.snapshot {
        Live(MapCameraMode.HEADING, listOf(RecordingIssue.BATTERY_OPTIMIZATION, RecordingIssue.POWER_SAVER))
    }

    @Test fun liveRecordingIdle() = paparazzi.snapshot { Live(MapCameraMode.HEADING, state = LiveRecordingState()) }

    @Test fun recordHealthDetail() = paparazzi.snapshot {
        GildamTheme {
            Surface {
                RecordingIssuesDetail(
                    listOf(RecordingIssue.LOCATION_OFF, RecordingIssue.BACKGROUND_LOCATION, RecordingIssue.APPROXIMATE_LOCATION,
                        RecordingIssue.GPS_WEAK, RecordingIssue.BATTERY_OPTIMIZATION, RecordingIssue.POWER_SAVER,
                        RecordingIssue.NOTIFICATIONS_OFF),
                    onOpenSettings = {}
                )
            }
        }
    }

    private val zone = ZoneId.of("Asia/Seoul")
    private fun at(m: Int, d: Int, h: Int) = LocalDateTime.of(2026, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()
    private fun rec(id: String, title: String, start: Long, km: Double, min: Long, type: TransportType) = ActivityEntity(
        id = id, title = title, transportType = type, status = ActivityStatus.COMPLETED,
        startedAtEpochMs = start, endedAtEpochMs = start + min * 60_000L, totalDurationMs = min * 60_000L,
        movingDurationMs = min * 55_000L, distanceMeters = km * 1000.0
    )
    private val items = listOf(
        rec("a", "Bukak ridge morning hike", at(11, 1, 7), 4.1, 72, TransportType.HIKE),
        rec("b", "Han River autumn ride", at(11, 1, 17), 12.4, 48, TransportType.BIKE),
        rec("c", "Namsan evening walk", at(9, 21, 18), 2.4, 38, TransportType.WALK),
        rec("d", "Morning run", at(9, 3, 6), 5.2, 31, TransportType.RUN)
    )

    @Test fun recordListWithGroupMenu() = paparazzi.snapshot {
        val groups = RecordGrouper.group(items, emptyMap(), RecordGroupMode.MONTH, zone)
        GildamTheme {
            Column {
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 6.dp)) {
                    Text(stringResource(R.string.list_title), style = MaterialTheme.typography.titleLarge)
                    RecordFilterRow(selectedTransportFilter = null, onSelectTransportFilter = {}, recordGroupMode = RecordGroupMode.MONTH, onSelectRecordGroup = {})
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                    Surface(shape = RoundedCornerShape(10.dp), shadowElevation = 6.dp, modifier = Modifier.width(160.dp)) {
                        Column(Modifier.padding(vertical = 6.dp)) { RecordGroupMenuItems(recordGroupMode = RecordGroupMode.MONTH, onSelect = {}) }
                    }
                }
                LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    groups.forEach { g ->
                        item { RecordGroupHeader(title = g.displayTitle(), count = g.activities.size) }
                        items(g.activities, key = { it.id }) { a -> ActivityCompactRow(activity = a, onClick = {}) }
                    }
                }
            }
        }
    }

    @Test fun settings() = paparazzi.snapshot {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            GildamTheme {
                SettingsScreen(
                    sessionState = NuniSessionState(),
                    syncStatus = SyncUiStatus.IDLE,
                    lastSyncSuccessAtMs = 0L,
                    serverTrackCount = null,
                    onKakaoLogin = {}, onSwitchAccount = {},
                    onDemoSimulateLogin = null,
                    onManualSync = {}, onLogout = {}, onNavigateBack = {},
                    isTopLevelTab = true
                )
            }
        }
    }

    @Test fun courseList() = paparazzi.snapshot {
        val thumb = (0 until 20).map { LatLonPoint(37.5 + it * 0.001, 127.0 + it * 0.0007) }
        CompositionLocalProvider(LocalInspectionMode provides true) {
            GildamTheme {
                CourseListScreen(
                    courses = listOf(
                        CourseListItem("1", "Bukhansan Baegundae trail", 8_400.0, 812.0, RuggednessGrade.VERY_HARD, 4.3 * 3600, thumb),
                        CourseListItem("2", "Seoul Forest loop", 3_200.0, 24.0, RuggednessGrade.EASY, 50 * 60.0, thumb),
                        CourseListItem("3", "Inwangsan fortress wall", 4_900.0, 320.0, RuggednessGrade.SOMEWHAT_HARD, 2.1 * 3600, thumb)
                    ),
                    isImporting = false, onImport = {}, onOpenCourse = {}, onOpenCompare = {}
                )
            }
        }
    }
}

/** 360dp 좁은 폰 · 영어 */
class EnglishNarrowPreviewTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(
            screenWidth = 1080, screenHeight = 2160, xdpi = 480, ydpi = 480,
            density = com.android.resources.Density.XXHIGH, locale = "en"
        ),
        theme = THEME, showSystemUi = false
    )

    @Test fun narrow360LiveRecording() = paparazzi.snapshot {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            GildamTheme {
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
                    fixedClockMs = 1_700_000_000_000L,
                    healthIssues = listOf(RecordingIssue.BACKGROUND_LOCATION, RecordingIssue.BATTERY_OPTIMIZATION, RecordingIssue.POWER_SAVER)
                )
            }
        }
    }
}
