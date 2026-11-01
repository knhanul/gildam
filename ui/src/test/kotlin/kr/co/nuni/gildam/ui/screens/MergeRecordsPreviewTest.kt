package kr.co.nuni.gildam.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.model.ActivityStatus
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.ui.theme.GildamTheme
import org.junit.Rule
import org.junit.Test

/** 기록 합치기 화면을 폰 크기로 렌더링해 이미지로 남깁니다. */
class MergeRecordsPreviewTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false
    )

    private fun rec(id: String, title: String, start: Long, km: Double, durMin: Long, type: TransportType) =
        ActivityEntity(
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

    @Test
    fun mergeListSelecting() {
        val base = 1_760_000_000_000L
        val items = listOf(
            rec("a", "아침 한강 산책", base, 2.4, 38, TransportType.WALK),
            rec("b", "점심 북악 능선", base + 7_200_000L, 4.1, 72, TransportType.HIKE),
            rec("c", "저녁 남산 자락", base + 18_000_000L, 3.3, 51, TransportType.WALK)
        )
        paparazzi.snapshot {
            GildamTheme {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("합칠 기록 고르기 · 2개", style = MaterialTheme.typography.titleMedium)
                    items.forEachIndexed { i, a ->
                        ActivityCompactRow(
                            activity = a,
                            onClick = {},
                            mergeSelected = if (i < 2) true else false
                        )
                    }
                }
            }
        }
    }

    @Test
    fun mergeConfirm() {
        val base = 1_760_000_000_000L
        val sources = listOf(
            rec("a", "아침 한강 산책", base, 2.4, 38, TransportType.WALK),
            rec("b", "점심 북악 능선", base + 7_200_000L, 4.1, 72, TransportType.HIKE),
            rec("c", "저녁 남산 자락", base + 18_000_000L, 3.3, 51, TransportType.WALK)
        )
        paparazzi.snapshot {
            GildamTheme {
                Box(Modifier.padding(16.dp)) {
                    MergeRecordsContent(
                        sources = sources,
                        defaultTitle = "아침 한강 산책",
                        merging = false,
                        onConfirm = {},
                        onCancel = {}
                    )
                }
            }
        }
    }
}
