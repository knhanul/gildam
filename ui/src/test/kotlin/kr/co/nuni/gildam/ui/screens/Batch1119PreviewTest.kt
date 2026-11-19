package kr.co.nuni.gildam.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import kr.co.nuni.gildam.data.course.RecordCourseBlock
import kr.co.nuni.gildam.data.model.GaugeCell
import kr.co.nuni.gildam.data.model.GaugeLayout
import kr.co.nuni.gildam.data.model.GaugeMetric
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.data.weather.PrecipType
import kr.co.nuni.gildam.data.weather.WeatherNow
import kr.co.nuni.gildam.data.weather.WeatherSource
import kr.co.nuni.gildam.map.MapCameraMode
import kr.co.nuni.gildam.recording.LatLonPoint
import kr.co.nuni.gildam.recording.LiveRecordingState
import kr.co.nuni.gildam.recording.LiveWeatherState
import kr.co.nuni.gildam.ui.theme.GildamTheme
import kr.co.nuni.gildam.ui.theme.TabularStatHeroStyle
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

private const val CLOCK = 1_731_628_800_000L
private val KST = ZoneId.of("Asia/Seoul")

/** 기상청 초단기실황처럼 하늘(SKY) 없이 기온·습도·강수·바람만 있는 값 */
private val kmaWeather = WeatherNow(
    source = WeatherSource.KMA,
    tempC = 5.8,
    humidity = 62.0,
    precipType = PrecipType.NONE,
    precip1hMm = 0.0,
    windSpeedMs = 3.2,
    windDirDeg = 330.0
)

private val rainyWeather = kmaWeather.copy(tempC = 12.4, precipType = PrecipType.RAIN, precip1hMm = 1.5, humidity = 91.0, windSpeedMs = 5.6, windDirDeg = 200.0)

/** 11/19 요구 사항 묶음 스냅샷: 코스 불러오기, 출발 전 날씨 한 줄, 바람 표시, 계기판 구성, 카메라 버튼 */
class Batch1119PreviewTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(locale = "ko"),
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false
    )

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            GildamTheme { Surface(color = MaterialTheme.colorScheme.surface) { content() } }
        }
    }

    @Test
    fun importSourceChooser() = paparazzi.snapshot {
        Themed { Column(Modifier.padding(top = 16.dp)) { ImportSourceContent(onFromFile = {}, onFromRecords = {}) } }
    }

    @Test
    fun recordPicker() = paparazzi.snapshot {
        Themed {
            Column(Modifier.padding(top = 16.dp)) {
                RecordPickerContent(
                    choices = listOf(
                        RecordCourseChoice("a", "북악산 새벽 걸음", CLOCK, 6_420.0, TransportType.HIKE),
                        RecordCourseChoice("b", "한강 자전거 퇴근길", CLOCK - 86_400_000L, 18_900.0, TransportType.BIKE),
                        RecordCourseChoice("c", "동네 한 바퀴", CLOCK - 2 * 86_400_000L, 2_300.0, TransportType.WALK),
                        RecordCourseChoice("d", "잠깐 켰다 끈 기록", CLOCK - 3 * 86_400_000L, 40.0, TransportType.WALK, RecordCourseBlock.TOO_SHORT),
                        RecordCourseChoice("e", "점 없는 기록", CLOCK - 4 * 86_400_000L, 0.0, TransportType.RUN, RecordCourseBlock.NO_POINTS)
                    ),
                    selected = setOf("a", "c"),
                    onToggle = {}, onConfirm = {}, onCancel = {},
                    zoneId = KST
                )
            }
        }
    }

    @Test
    fun startWeatherRows() = paparazzi.snapshot {
        Themed {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label("걷기 · 넓은 화면 (기상청: 하늘 정보 없음)")
                StartWeatherCard(kmaWeather, TransportType.WALK)
                Label("자전거 · 바람 먼저·굵게")
                StartWeatherCard(kmaWeather, TransportType.BIKE)
                Label("비 오는 날 · 걷기")
                StartWeatherCard(rainyWeather, TransportType.WALK)
                Label("좁은 화면 300dp · 걷기 (습도부터 빠짐)")
                StartWeatherCard(kmaWeather, TransportType.WALK, Modifier.width(300.dp))
                Label("좁은 화면 300dp · 자전거")
                StartWeatherCard(kmaWeather, TransportType.BIKE, Modifier.width(300.dp))
                Label("아주 좁은 화면 240dp · 자전거")
                StartWeatherCard(rainyWeather, TransportType.BIKE, Modifier.width(240.dp))
            }
        }
    }

    @Test
    fun liveWeatherCellBothModes() = paparazzi.snapshot {
        val style = TabularStatHeroStyle.copy(fontSize = GAUGE_VALUE_SP.sp, lineHeight = (GAUGE_VALUE_SP + 5).sp)
        Themed {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label("진행 방향 위 (북북서풍, 진행 방향을 바꿔 가며)")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LiveWeatherGaugeCard(kmaWeather, false, TransportType.BIKE, style, false, Modifier.weight(1f), headingUp = true, mapBearingDeg = 330f)
                    LiveWeatherGaugeCard(kmaWeather, false, TransportType.WALK, style, false, Modifier.weight(1f), headingUp = true, mapBearingDeg = 330f)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LiveWeatherGaugeCard(kmaWeather, false, TransportType.BIKE, style, false, Modifier.weight(1f), headingUp = true, mapBearingDeg = 150f)
                    LiveWeatherGaugeCard(kmaWeather, false, TransportType.BIKE, style, false, Modifier.weight(1f), headingUp = true, mapBearingDeg = 60f)
                }
                Label("위: 맞바람 · 아래 왼쪽: 뒷바람 · 아래 오른쪽: 옆바람")
                Label("북쪽 위 (화살표 실제 방위 고정, 회색 + N)")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LiveWeatherGaugeCard(kmaWeather, false, TransportType.BIKE, style, false, Modifier.weight(1f), headingUp = false, mapBearingDeg = 0f)
                    LiveWeatherGaugeCard(kmaWeather, false, TransportType.WALK, style, false, Modifier.weight(1f), headingUp = false, mapBearingDeg = 0f)
                }
            }
        }
    }

    @Test
    fun gaugeLayoutSettings() = paparazzi.snapshot {
        Themed {
            GaugeLayoutScreen(
                layout = GaugeLayout.DEFAULT.moved(6, 0).toggled(GaugeCell.CLOCK, false),
                onChange = {}, onReset = {}, onNavigateBack = {}
            )
        }
    }

    @Test
    fun liveRecordingCustomGaugeOddCells() = paparazzi.snapshot {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            GildamTheme {
                LiveRecordingScreen(
                    liveState = liveState(TransportType.BIKE),
                    selectedTransport = TransportType.BIKE,
                    onSelectTransport = {}, onStartRecording = {}, onPauseRecording = {}, onResumeRecording = {},
                    onStopRecording = {}, onAddWaypoint = { _, _, _ -> }, onNavigateToList = {}, onNavigateToSettings = {},
                    showTopNavIcons = false,
                    defaultCameraMode = MapCameraMode.FOLLOW,
                    overlayMetric = GaugeMetric.CURRENT_SPEED,
                    fixedClockMs = CLOCK,
                    healthIssues = emptyList(),
                    // 날씨를 맨 앞으로, 현재 시각 끔 → 5칸 (마지막 칸 넓게)
                    gaugeLayout = GaugeLayout.DEFAULT.moved(6, 0).toggled(GaugeCell.CLOCK, false)
                )
            }
        }
    }

    @Test
    fun preStartWalkWithWeatherRow() = paparazzi.snapshot {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            GildamTheme {
                LiveRecordingScreen(
                    liveState = LiveRecordingState(),
                    selectedTransport = TransportType.WALK,
                    onSelectTransport = {}, onStartRecording = {}, onPauseRecording = {}, onResumeRecording = {},
                    onStopRecording = {}, onAddWaypoint = { _, _, _ -> }, onNavigateToList = {}, onNavigateToSettings = {},
                    showTopNavIcons = false,
                    fixedClockMs = CLOCK,
                    healthIssues = emptyList(),
                    startWeather = StartWeatherUi.Ready(kmaWeather)
                )
            }
        }
    }

    @Test
    fun waypointSheetWithCamera() = paparazzi.snapshot {
        Themed {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                WaypointSheetContent("", {}, "", {}, null, {}, {}, {})
                WaypointSheetContent("전망대에서 본 서울", {}, "", {}, WaypointPhotoSource.CAMERA, {}, {}, {})
            }
        }
    }

    @Composable
    private fun Label(t: String) = Text(t, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    private fun liveState(type: TransportType): LiveRecordingState {
        val track = (0 until 40).map { i -> LatLonPoint(37.5665 + i * 0.00014, 126.978 + i * 0.00016) }
        return LiveRecordingState(
            isRecording = true,
            transportType = type,
            elapsedTotalMs = (42 * 60 + 15) * 1000L,
            elapsedMovingMs = 39 * 60 * 1000L,
            distanceMeters = 9_420.0,
            currentSpeedMps = 5.8f,
            movingAvgSpeedMps = 5.2f,
            elevationGainM = 125f,
            elevationLossM = 98f,
            liveWeather = LiveWeatherState(weather = kmaWeather, fetchedAtEpochMs = CLOCK - 5 * 60_000L),
            currentBearingDeg = 35f,
            currentLocation = track.last(),
            liveTrackPoints = track
        )
    }
}
