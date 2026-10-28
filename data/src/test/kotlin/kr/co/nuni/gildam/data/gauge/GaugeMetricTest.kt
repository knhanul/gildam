package kr.co.nuni.gildam.data.gauge

import kr.co.nuni.gildam.data.model.GaugeFormat
import kr.co.nuni.gildam.data.model.GaugeMetric
import kr.co.nuni.gildam.data.model.TransportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class GaugeMetricTest {

    @Test fun defaultsMatchOwnerRequest() {
        // 승희님 요청: 자전거=현재 속도, 자동차=이동 평균 속도, 달리기=누적 거리, 걷기=총시간, 등산=누적 거리
        assertEquals(GaugeMetric.CURRENT_SPEED, GaugeMetric.defaultFor(TransportType.BIKE))
        assertEquals(GaugeMetric.MOVING_AVG_SPEED, GaugeMetric.defaultFor(TransportType.CAR))
        assertEquals(GaugeMetric.DISTANCE, GaugeMetric.defaultFor(TransportType.RUN))
        assertEquals(GaugeMetric.TOTAL_TIME, GaugeMetric.defaultFor(TransportType.WALK))
        assertEquals(GaugeMetric.DISTANCE, GaugeMetric.defaultFor(TransportType.HIKE))
    }

    @Test fun everyTransportHasADefault() {
        TransportType.entries.forEach { assertTrue(GaugeMetric.displayOrder.contains(GaugeMetric.defaultFor(it))) }
    }

    @Test fun totalIsFiveAndClockIsIncluded() {
        assertEquals(5, GaugeMetric.displayOrder.size)
        assertEquals(5, GaugeMetric.displayOrder.toSet().size)
        assertTrue(GaugeMetric.displayOrder.contains(GaugeMetric.CLOCK))
        assertEquals(5, GaugeMetric.selectable.size)
    }

    @Test fun bottomAlwaysFourWithoutOverlay() {
        GaugeMetric.displayOrder.forEach { overlay ->
            val bottom = GaugeMetric.bottomFor(overlay)
            assertEquals(4, bottom.size)
            assertEquals(GaugeMetric.displayOrder.size - 1, bottom.toSet().size)
            assertTrue("지도 위 값은 지도 아래에서 빠져야 함: $overlay", !bottom.contains(overlay))
            assertEquals(2, GaugeMetric.heroesFor(overlay).size)
            assertEquals(2, GaugeMetric.subsFor(overlay).size)
        }
    }

    @Test fun clockOverlayKeepsFourComputedValuesBelow() {
        val bottom = GaugeMetric.bottomFor(GaugeMetric.CLOCK)
        assertEquals(
            listOf(GaugeMetric.DISTANCE, GaugeMetric.TOTAL_TIME, GaugeMetric.CURRENT_SPEED, GaugeMetric.MOVING_AVG_SPEED),
            bottom
        )
    }

    @Test fun labelsCarryUnits() {
        assertEquals("누적 거리 (km)", GaugeMetric.DISTANCE.labelWithUnit)
        assertEquals("현재 속도 (km/h)", GaugeMetric.CURRENT_SPEED.labelWithUnit)
        assertEquals("이동 평균 속도 (km/h)", GaugeMetric.MOVING_AVG_SPEED.labelWithUnit)
        assertEquals("총 시간", GaugeMetric.TOTAL_TIME.labelWithUnit)
        assertEquals("현재 시간", GaugeMetric.CLOCK.labelWithUnit)
        assertNull(GaugeMetric.CLOCK.unit)
    }

    @Test fun formatDistanceAndSpeed() {
        assertEquals("0.00", GaugeFormat.distanceKm(0.0))
        assertEquals("2.24", GaugeFormat.distanceKm(2240.0))
        assertEquals("18.53", GaugeFormat.distanceKm(18534.0))
        assertEquals("0.00", GaugeFormat.distanceKm(-5.0)) // 음수는 0으로 막음
        assertEquals("0.0", GaugeFormat.speedKmh(0f))
        assertEquals("5.0", GaugeFormat.speedKmh(1.3889f))
        assertEquals("0.0", GaugeFormat.speedKmh(-3f))
    }

    @Test fun formatDuration() {
        assertEquals("00:00:00", GaugeFormat.durationHms(0))
        assertEquals("00:27:18", GaugeFormat.durationHms(1_638_000))
        assertEquals("01:00:00", GaugeFormat.durationHms(3_600_000))
        assertEquals("00:00:00", GaugeFormat.durationHms(-9_000))
        assertEquals("10:01:05", GaugeFormat.durationHms((10L * 3600L + 65L) * 1000L))
    }

    @Test fun formatClockInSeoul() {
        val zone = ZoneId.of("Asia/Seoul")
        // 2026-10-28T09:20:05Z == 18:20:05 KST
        val epoch = 1793092805000L
        assertEquals("18:20", GaugeFormat.clockHm(epoch, zone))
        assertEquals("18:20:05", GaugeFormat.clockHms(epoch, zone))
    }
}
