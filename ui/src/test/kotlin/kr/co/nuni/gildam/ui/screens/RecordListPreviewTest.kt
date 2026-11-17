package kr.co.nuni.gildam.ui.screens

import kr.co.nuni.gildam.ui.l10n.displayTitle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import kr.co.nuni.gildam.data.list.ActivityPlaceInfo
import kr.co.nuni.gildam.data.list.RecordGroup
import kr.co.nuni.gildam.data.list.RecordGrouper
import kr.co.nuni.gildam.data.list.RecordGroupMode
import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.model.ActivityStatus
import kr.co.nuni.gildam.data.model.SyncState
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.ui.theme.GildamTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 기록 보관함의 리스트 보기와 최근순·월별·지역별 묶음을 폰 크기로 렌더링해 이미지로 남깁니다. */
class RecordListPreviewTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(locale = "ko"),
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false
    )

    private val zone = ZoneId.of("Asia/Seoul")
    private val previewToday = LocalDate.of(2026, 11, 17)

    private fun at(year: Int, month: Int, day: Int, hour: Int = 9, minute: Int = 0) =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun rec(
        id: String,
        title: String,
        start: Long,
        km: Double,
        durMin: Long,
        type: TransportType,
        durSecExtra: Long = 0L,
        sync: SyncState = SyncState.LOCAL_ONLY
    ): ActivityEntity {
        val totalMs = durMin * 60_000L + durSecExtra * 1_000L
        val movingMs = (totalMs * 92) / 100
        return ActivityEntity(
            id = id,
            title = title,
            transportType = type,
            status = ActivityStatus.COMPLETED,
            startedAtEpochMs = start,
            endedAtEpochMs = start + totalMs,
            totalDurationMs = totalMs,
            movingDurationMs = movingMs,
            distanceMeters = km * 1000.0,
            movingAvgSpeedMps = if (movingMs > 0) (km * 1000.0 / (movingMs / 1000.0)).toFloat() else 0f,
            syncState = sync
        )
    }

    private val cardItems = listOf(
        rec("a", "11월 1일 07:00 등산 길이야기", at(2026, 11, 1, 7), 4.1, 72, TransportType.HIKE, sync = SyncState.SYNCED),
        rec("b", "가을 한강 자전거", at(2026, 11, 1, 17), 12.4, 48, TransportType.BIKE),
        rec("c", "9월 21일 18:00 걷기 길이야기", at(2026, 9, 21, 18), 2.4, 38, TransportType.WALK),
        rec("d", "아침 동네 러닝", at(2026, 9, 3, 6), 5.2, 31, TransportType.RUN)
    )

    private val cardPlaces = mapOf(
        "a" to ActivityPlaceInfo(regionName = "서울 종로구", startPlace = "부암동", endPlace = "삼청동", placesResolved = true),
        "b" to ActivityPlaceInfo(regionName = "서울 마포구", startPlace = "망원동", endPlace = "여의도동", placesResolved = true),
        "c" to ActivityPlaceInfo(regionName = "서울 중구", startPlace = "회현동", endPlace = "회현동", placesResolved = true)
    )

    private val commuteItems = listOf(
        rec("c_evening", "11월 17일 18:42 자전거 길이야기", at(2026, 11, 17, 18, 42), 19.84, 83, TransportType.BIKE, durSecExtra = 14, sync = SyncState.SYNCED),
        rec("c_morning", "출근길", at(2026, 11, 17, 7, 8), 19.71, 80, TransportType.BIKE, durSecExtra = 56, sync = SyncState.SYNCED),
        rec("y_walk", "11월 16일 12:20 걷기 길이야기", at(2026, 11, 16, 12, 20), 1.85, 24, TransportType.WALK, durSecExtra = 10),
        rec("old_no_place", "11월 15일 09:15 달리기 길이야기", at(2026, 11, 15, 9, 15), 5.12, 28, TransportType.RUN, durSecExtra = 40)
    )

    private val commutePlaces = mapOf(
        "c_evening" to ActivityPlaceInfo(regionName = "서울 강남구", startPlace = "역삼동", endPlace = "매탄동", placesResolved = true),
        "c_morning" to ActivityPlaceInfo(regionName = "경기 수원시 영통구", startPlace = "매탄동", endPlace = "역삼동", placesResolved = true),
        "y_walk" to ActivityPlaceInfo(regionName = "서울 강남구", startPlace = "역삼동", endPlace = "역삼동", placesResolved = true)
    )

    private val compactItems = listOf(
        rec("r1", "종로 골목길 산책", at(2026, 11, 1, 8), 2.4, 38, TransportType.WALK, sync = SyncState.SYNCED),
        rec("r2", "10월 26일 07:00 등산 길이야기", at(2026, 10, 26, 7), 6.8, 96, TransportType.HIKE),
        rec("r3", "10월 18일 16:00 자전거 길이야기", at(2026, 10, 18, 16), 14.2, 52, TransportType.BIKE),
        rec("r4", "9월 30일 06:00 달리기 길이야기", at(2026, 9, 30, 6), 5.4, 29, TransportType.RUN)
    )

    private val compactRegions = mapOf(
        "r1" to "서울 종로구",
        "r2" to "서울 종로구",
        "r3" to "경기 성남시 분당구",
        "r4" to null
    )

    private val compactPlaces = mapOf(
        "r1" to ActivityPlaceInfo(regionName = "서울 종로구", startPlace = "익선동", endPlace = "삼청동", placesResolved = true),
        "r2" to ActivityPlaceInfo(regionName = "서울 종로구", startPlace = "부암동", endPlace = "정릉동", placesResolved = true),
        "r3" to ActivityPlaceInfo(regionName = "경기 성남시 분당구", startPlace = "정자동", endPlace = "수서동", placesResolved = true),
        "r4" to ActivityPlaceInfo(regionName = null, startPlace = null, endPlace = null, placesResolved = true)
    )

    private fun groupList(
        items: List<ActivityEntity>,
        regions: Map<String, String?>,
        mode: RecordGroupMode
    ): List<RecordGroup> = RecordGrouper.group(items, regions, mode, zone, previewToday)

    /** 실제 화면과 같은 상단 구성: 제목 + 활동 필터·묶어 보기가 함께 있는 한 줄 */
    @androidx.compose.runtime.Composable
    private fun PreviewTopBar(groupMode: RecordGroupMode) {
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 6.dp)) {
            Text("길담 보관함", style = MaterialTheme.typography.titleLarge)
            RecordFilterRow(
                selectedTransportFilter = null,
                onSelectTransportFilter = {},
                recordGroupMode = groupMode,
                onSelectRecordGroup = {}
            )
        }
    }

    @Test
    fun listRecentCommute() {
        val groups = groupList(commuteItems, emptyMap(), RecordGroupMode.RECENT)
        paparazzi.snapshot {
            GildamTheme {
                Column {
                    PreviewTopBar(RecordGroupMode.RECENT)
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        HiddenShortRecordsBar(count = 2, onShow = {}, onDeleteAll = {})
                    }
                    LazyColumn(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groups.forEach { g ->
                            item { RecordGroupHeader(title = g.displayTitle(previewToday), count = g.activities.size) }
                            items(g.activities, key = { it.id }) { a ->
                                ActivityCompactRow(
                                    activity = a,
                                    placeInfo = commutePlaces[a.id],
                                    onClick = {},
                                    zoneId = zone
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun listMonthWithMenu() {
        val groups = groupList(cardItems, emptyMap(), RecordGroupMode.MONTH)
        paparazzi.snapshot {
            GildamTheme {
                Column {
                    PreviewTopBar(RecordGroupMode.MONTH)
                    LazyColumn(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groups.forEach { g ->
                            item { RecordGroupHeader(title = g.displayTitle(previewToday), count = g.activities.size) }
                            items(g.activities, key = { it.id }) { a ->
                                ActivityCompactRow(
                                    activity = a,
                                    placeInfo = cardPlaces[a.id],
                                    onClick = {},
                                    zoneId = zone
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** 좁은 폰(360dp)에서도 활동 칩과 묶어 보기 버튼, 통계 줄이 잘리지 않는지 확인용 */
    @Test
    fun filterRowNarrow() {
        paparazzi.snapshot {
            GildamTheme {
                Column(Modifier.width(360.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecordFilterRow(
                        selectedTransportFilter = TransportType.BIKE,
                        onSelectTransportFilter = {},
                        recordGroupMode = RecordGroupMode.REGION,
                        onSelectRecordGroup = {}
                    )
                    RecordFilterRow(
                        selectedTransportFilter = null,
                        onSelectTransportFilter = {},
                        recordGroupMode = RecordGroupMode.RECENT,
                        onSelectRecordGroup = {}
                    )
                    ActivityCompactRow(
                        activity = commuteItems[0],
                        placeInfo = commutePlaces["c_evening"],
                        onClick = {},
                        zoneId = zone
                    )
                    ActivityCompactRow(
                        activity = commuteItems[1],
                        placeInfo = commutePlaces["c_morning"],
                        onClick = {},
                        zoneId = zone
                    )
                }
            }
        }
    }

    /** 오른쪽 끝 작은 버튼을 눌렀을 때 열리는 선택 메뉴 */
    @Test
    fun listGroupMenuOpen() {
        paparazzi.snapshot {
            GildamTheme {
                Column {
                    PreviewTopBar(RecordGroupMode.REGION)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            shadowElevation = 6.dp,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.width(210.dp)
                        ) {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                RecordGroupMenuItems(
                                    recordGroupMode = RecordGroupMode.REGION,
                                    onSelect = {},
                                    hideShortRecords = true,
                                    onToggleHideShortRecords = {}
                                )
                            }
                        }
                    }
                    Spacer(Modifier.padding(top = 6.dp))
                }
            }
        }
    }

    @Test
    fun listCompactRegion() {
        val groups = groupList(compactItems, compactRegions, RecordGroupMode.REGION)
        paparazzi.snapshot {
            GildamTheme {
                Column {
                    PreviewTopBar(RecordGroupMode.REGION)
                    LazyColumn(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groups.forEach { g ->
                            item { RecordGroupHeader(title = g.displayTitle(previewToday), count = g.activities.size) }
                            items(g.activities, key = { it.id }) { a ->
                                ActivityCompactRow(
                                    activity = a,
                                    placeInfo = compactPlaces[a.id],
                                    onClick = {},
                                    zoneId = zone
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun listCompactMerge() {
        val groups = groupList(compactItems, compactRegions, RecordGroupMode.REGION)
        val picked = setOf("r1", "r3")
        paparazzi.snapshot {
            GildamTheme {
                Column {
                    Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("합칠 기록 고르기", style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            "끝난 기록을 두 개 이상 골라 주세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groups.forEach { g ->
                            item { RecordGroupHeader(title = g.displayTitle(previewToday), count = g.activities.size) }
                            items(g.activities, key = { it.id }) { a ->
                                ActivityCompactRow(
                                    activity = a,
                                    placeInfo = compactPlaces[a.id],
                                    onClick = {},
                                    mergeSelected = a.id in picked,
                                    zoneId = zone
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
