package kr.co.nuni.gildam.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import kr.co.nuni.gildam.data.gpxcompare.GpxPoint
import kr.co.nuni.gildam.data.gpxcompare.GpxRuggedness
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.recording.LatLonPoint
import kr.co.nuni.gildam.ui.theme.GildamTheme
import org.junit.Rule
import org.junit.Test

/** 코스 상세(정방향·반대 방향 미리보기)와 따라가기 띠(한 줄 알약, 반대 방향 배지, 시간 추정 없음) 스냅샷 */
class CoursePreviewTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(locale = "ko"),
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false
    )

    private val gpxPoints: List<GpxPoint> = (0..40).map { i ->
        val ele = if (i <= 32) 80.0 + i * 7.5 else 320.0 - (i - 32) * 4.0
        GpxPoint(37.570 + i * 0.0005, 126.970 + i * 0.0006, ele)
    }
    private val forwardResult = GpxRuggedness.analyze("북악 능선 코스", gpxPoints)
    private val reversedResult = GpxRuggedness.analyze("북악 능선 코스", gpxPoints.reversed())
    private val forwardRoute = gpxPoints.map { LatLonPoint(it.lat, it.lon) }

    private val detailUi = CourseDetailUi(
        id = "course-bukak",
        name = "북악 능선 코스",
        result = forwardResult,
        route = forwardRoute,
        reversedResult = reversedResult,
        reversedRoute = forwardRoute.asReversed()
    )

    @Test
    fun courseDetailForward() = paparazzi.snapshot {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            GildamTheme {
                CourseDetailScreen(
                    detail = detailUi,
                    onNavigateBack = {},
                    onFollow = { _, _, _ -> },
                    onDelete = {},
                    suggestedActivity = TransportType.HIKE,
                    initialReversed = false
                )
            }
        }
    }

    @Test
    fun courseDetailReversed() = paparazzi.snapshot {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            GildamTheme {
                CourseDetailScreen(
                    detail = detailUi,
                    onNavigateBack = {},
                    onFollow = { _, _, _ -> },
                    onDelete = {},
                    suggestedActivity = TransportType.BIKE,
                    initialReversed = true
                )
            }
        }
    }

    @Test
    fun followCardsForwardAndReverse() = paparazzi.snapshot {
        GildamTheme {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FollowCourseCard(
                    f = CourseFollowUi(
                        name = "탄천 자전거길",
                        route = forwardRoute,
                        totalM = 14_200.0,
                        remainingM = 8_500.0,
                        isOffCourse = false,
                        offsetM = 4.0,
                        reversed = false
                    ),
                    onStop = {}
                )
                FollowCourseCard(
                    f = CourseFollowUi(
                        name = "탄천 자전거길",
                        route = forwardRoute.asReversed(),
                        totalM = 14_200.0,
                        remainingM = 5_100.0,
                        isOffCourse = false,
                        offsetM = 6.0,
                        reversed = true
                    ),
                    onStop = {}
                )
                FollowCourseCard(
                    f = CourseFollowUi(
                        name = "탄천 자전거길",
                        route = forwardRoute.asReversed(),
                        totalM = 14_200.0,
                        remainingM = 5_100.0,
                        isOffCourse = true,
                        offsetM = 64.0,
                        reversed = true
                    ),
                    onStop = {}
                )
            }
        }
    }
}
