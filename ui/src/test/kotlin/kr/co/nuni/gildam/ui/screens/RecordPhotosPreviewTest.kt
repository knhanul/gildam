package kr.co.nuni.gildam.ui.screens

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import kr.co.nuni.gildam.data.local.entity.ActivityDetailBundle
import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.local.entity.TrackPointEntity
import kr.co.nuni.gildam.data.local.entity.WaypointEntity
import kr.co.nuni.gildam.data.local.entity.WaypointWithPhoto
import kr.co.nuni.gildam.data.model.ActivityStatus
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.data.photo.PhotoCandidate
import kr.co.nuni.gildam.data.photo.RecordPhotoMatcher
import kr.co.nuni.gildam.ui.GildamViewModel
import kr.co.nuni.gildam.ui.theme.GildamTheme
import org.junit.Rule
import org.junit.Test

private const val START = 1_763_502_180_000L // 11/19 06:43 KST 무렵

private val points = (0 until 120).map { i ->
    TrackPointEntity(
        id = i.toLong() + 1, activityId = "bike-photos", seq = i,
        latitude = 37.40 + i * 0.0012, longitude = 127.10 + i * 0.0009,
        recordedAtEpochMs = START + i * 30_000L
    )
}
private val route = RecordPhotoMatcher.routeWithDistance(points.map { Triple(it.latitude, it.longitude, it.recordedAtEpochMs) })

/** 29장: 모두 GPS 없음(권한 없을 때처럼) → 경로 기준 위치로 추정 */
private val photos29 = RecordPhotoMatcher.match(
    START, START + 3_600_000L, route,
    (0 until 29).map { i -> PhotoCandidate("content://media/external/images/media/${100 + i}", START + 60_000L + i * 110_000L) }
)

private val activity = ActivityEntity(
    id = "bike-photos", title = "자전거 길이야기", transportType = TransportType.BIKE,
    status = ActivityStatus.COMPLETED, startedAtEpochMs = START, endedAtEpochMs = START + 3_600_000L,
    totalDurationMs = 3_600_000L, movingDurationMs = 3_300_000L, distanceMeters = 20_640.0,
    movingAvgSpeedMps = 5.7f, elevationGainMeters = 180f
)

/** 매성재: 30번째 경로 점 자리, 15분쯤 */
private val waypoints = listOf(
    WaypointWithPhoto(
        WaypointEntity(id = "w1", activityId = activity.id, latitude = points[30].latitude, longitude = points[30].longitude,
            recordedAtEpochMs = points[30].recordedAtEpochMs, memoText = "매성재"),
        null
    ),
    WaypointWithPhoto(
        WaypointEntity(id = "w2", activityId = activity.id, latitude = points[100].latitude, longitude = points[100].longitude,
            recordedAtEpochMs = points[100].recordedAtEpochMs + 20 * 60_000L, memoText = "내리막 끝 쉼터"),
        null
    )
)

/** 기록 상세의 사진 구역: 접기·펼치기, 경로 기준 위치, 웨이포인트 필터 */
class RecordPhotosPreviewTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(locale = "ko", screenHeight = 4200),
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false
    )

    @Test fun detailPhotosCollapsedWithLocationAsk() = paparazzi.snapshot {
        Detail(GildamViewModel.RecordPhotosState(photos = photos29, searched = true, locationRedacted = true))
    }

    @Test fun detailPhotosExpandedAll29() = paparazzi.snapshot {
        Detail(GildamViewModel.RecordPhotosState(photos = photos29, searched = true), expanded = true)
    }

    @Test fun detailWaypointSelectedFiltersPhotos() = paparazzi.snapshot {
        Detail(GildamViewModel.RecordPhotosState(photos = photos29, searched = true), selected = "w1")
    }

    @Test fun sectionFilterEmptyAndNoLocation() = paparazzi.snapshot {
        val noRoute = RecordPhotoMatcher.match(START, START + 3_600_000L, emptyList(),
            listOf(PhotoCandidate("content://media/external/images/media/1", START + 60_000L)))
        val gps = RecordPhotoMatcher.match(START, START + 3_600_000L, route,
            listOf(PhotoCandidate("content://media/external/images/media/2", START + 90_000L, points[3].latitude, points[3].longitude)))
        Wrap {
            androidx.compose.foundation.layout.Column {
            LazyColumn(androidx.compose.ui.Modifier.height(260.dp)) {
                // 필터를 골랐지만 근처 사진이 없을 때
                recordPhotoSectionItems(
                    loading = false, permissionDenied = false, photos = photos29, truncated = false,
                    locationRedacted = false, expanded = false,
                    filter = PhotoPlaceFilter("w9", "내리막 끝 쉼터", emptyList()),
                    onToggleExpanded = {}, onClearFilter = {}, onOpenPhoto = {},
                    onRequestPermission = {}, onRequestLocationAccess = {}
                )
            }
            LazyColumn {
                // GPS 사진 · 경로 기준 · 위치 정보 없음(경로도 없음) 배지 비교
                recordPhotoSectionItems(
                    loading = false, permissionDenied = false, photos = gps + photos29.take(1) + noRoute, truncated = false,
                    locationRedacted = false, expanded = false, filter = null,
                    onToggleExpanded = {}, onClearFilter = {}, onOpenPhoto = {},
                    onRequestPermission = {}, onRequestLocationAccess = {}
                )
            }
            }
        }
    }

    @Composable
    private fun Wrap(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalActivityResultRegistryOwner provides registryOwner()
        ) {
            GildamTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } }
        }
    }

    @Composable
    private fun Detail(state: GildamViewModel.RecordPhotosState, expanded: Boolean = false, selected: String? = null) = Wrap {
        ActivityDetailScreen(
            bundle = ActivityDetailBundle(activity = activity, waypoints = waypoints),
            trackPoints = points,
            onVerifyPhotos = {},
            onRelinkMissingPhoto = { _, _ -> },
            onRemoveMissingPhotoKeepMemo = { _, _ -> },
            onNavigateToSafeExport = {},
            onNavigateBack = {},
            recordPhotos = state,
            initialSelectedWaypointId = selected,
            initialPhotosExpanded = expanded
        )
    }
}

private fun registryOwner(): ActivityResultRegistryOwner =
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
