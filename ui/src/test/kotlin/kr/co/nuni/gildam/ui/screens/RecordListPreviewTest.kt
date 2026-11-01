package kr.co.nuni.gildam.ui.screens

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
import kr.co.nuni.gildam.data.list.RecordGroup
import kr.co.nuni.gildam.data.list.RecordGrouper
import kr.co.nuni.gildam.data.list.RecordGroupMode
import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.model.ActivityStatus
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.ui.theme.GildamTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** 기록 보관함의 카드/리스트 보기와 월별·지역별 묶음을 폰 크기로 렌더링해 이미지로 남깁니다. */
class RecordListPreviewTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false
    )

    private val zone = ZoneId.of("Asia/Seoul")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 9) =
        LocalDateTime.of(year, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun rec(
        id: String,
        title: String,
        start: Long,
        km: Double,
        durMin: Long,
        type: TransportType
    ) = ActivityEntity(
        id = id,
        title = title,
        transportType = type,
        status = ActivityStatus.COMPLETED,
        startedAtEpochMs = start,
        endedAtEpochMs = start + durMin * 60_000L,
        totalDurationMs = durMin * 60_000L,
        movingDurationMs = durMin * 55_000L,
        distanceMeters = km * 1000.0
    )

    private val cardItems = listOf(
        rec("a", "11월 첫 북악 능선", at(2026, 11, 1, 7), 4.1, 72, TransportType.HIKE),
        rec("b", "가을 한강 자전거", at(2026, 11, 1, 17), 12.4, 48, TransportType.BIKE),
        rec("c", "9월 남산 자락 산책", at(2026, 9, 21, 18), 2.4, 38, TransportType.WALK),
        rec("d", "9월 아침 러닝", at(2026, 9, 3, 6), 5.2, 31, TransportType.RUN)
    )

    private val compactItems = listOf(
        rec("r1", "종로 골목길 산책", at(2026, 11, 1, 8), 2.4, 38, TransportType.WALK),
        rec("r2", "북악 능선 종주", at(2026, 10, 26, 7), 6.8, 96, TransportType.HIKE),
        rec("r3", "분당 탄천 자전거", at(2026, 10, 18, 16), 14.2, 52, TransportType.BIKE),
        rec("r4", "아침 러닝 기록", at(2026, 9, 30, 6), 5.4, 29, TransportType.RUN)
    )

    private val compactRegions = mapOf(
        "r1" to "서울 종로구",
        "r2" to "서울 종로구",
        "r3" to "경기 성남시",
        "r4" to null
    )

    private fun groupList(
        items: List<ActivityEntity>,
        regions: Map<String, String?>,
        mode: RecordGroupMode
    ): List<RecordGroup> = RecordGrouper.group(items, regions, mode, zone)

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
    fun listMonthWithMenu() {
        val groups = groupList(cardItems, emptyMap(), RecordGroupMode.MONTH)
        paparazzi.snapshot {
            GildamTheme {
                Column {
                    PreviewTopBar(RecordGroupMode.MONTH)
                    LazyColumn(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        groups.forEach { g ->
                            item { RecordGroupHeader(title = g.title, count = g.activities.size) }
                            items(g.activities, key = { it.id }) { a ->
                                ActivityCompactRow(activity = a, onClick = {})
                            }
                        }
                    }
                }
            }
        }
    }

    /** 좁은 폰(360dp)에서도 활동 칩과 묶어 보기 버튼이 한 줄에 겹치지 않는지 확인용 */
    @Test
    fun filterRowNarrow() {
        paparazzi.snapshot {
            GildamTheme {
                Column(Modifier.width(360.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    RecordFilterRow(
                        selectedTransportFilter = TransportType.BIKE,
                        onSelectTransportFilter = {},
                        recordGroupMode = RecordGroupMode.REGION,
                        onSelectRecordGroup = {}
                    )
                    Spacer(Modifier.padding(top = 4.dp))
                    RecordFilterRow(
                        selectedTransportFilter = null,
                        onSelectTransportFilter = {},
                        recordGroupMode = RecordGroupMode.RECENT,
                        onSelectRecordGroup = {}
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
                            modifier = Modifier.width(160.dp)
                        ) {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                RecordGroupMenuItems(
                                    recordGroupMode = RecordGroupMode.REGION,
                                    onSelect = {}
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
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        groups.forEach { g ->
                            item { RecordGroupHeader(title = g.title, count = g.activities.size) }
                            items(g.activities, key = { it.id }) { a ->
                                ActivityCompactRow(activity = a, onClick = {})
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
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        groups.forEach { g ->
                            item { RecordGroupHeader(title = g.title, count = g.activities.size) }
                            items(g.activities, key = { it.id }) { a ->
                                ActivityCompactRow(
                                    activity = a,
                                    onClick = {},
                                    mergeSelected = a.id in picked
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
