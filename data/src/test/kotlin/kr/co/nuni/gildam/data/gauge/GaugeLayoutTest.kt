package kr.co.nuni.gildam.data.gauge

import kr.co.nuni.gildam.data.model.GaugeCell
import kr.co.nuni.gildam.data.model.GaugeLayout
import kr.co.nuni.gildam.data.model.GaugeMetric
import org.junit.Assert.assertEquals
import org.junit.Test

class GaugeLayoutTest {
    @Test fun defaultRecordingIsSixCellsLikeBefore() {
        val cells = GaugeLayout.DEFAULT.gridCells(GaugeMetric.CURRENT_SPEED, recording = true)
        assertEquals(
            listOf(GaugeCell.DISTANCE, GaugeCell.TOTAL_TIME, GaugeCell.MOVING_AVG_SPEED, GaugeCell.CLOCK, GaugeCell.ELEVATION, GaugeCell.WEATHER),
            cells
        )
    }

    @Test fun beforeRecordingHidesElevationAndWeather() {
        assertEquals(4, GaugeLayout.DEFAULT.gridCells(GaugeMetric.DISTANCE, recording = false).size)
    }

    @Test fun weatherOrElevationOnMapIsSkippedBelow() {
        val weatherUp = GaugeLayout.DEFAULT.gridCells(GaugeMetric.WEATHER, recording = true)
        assertEquals(6, weatherUp.size)
        assertEquals(false, GaugeCell.WEATHER in weatherUp)
        val elevUp = GaugeLayout.DEFAULT.gridCells(GaugeMetric.ELEVATION, recording = true)
        assertEquals(false, GaugeCell.ELEVATION in elevUp)
        // 기록 전에는 둘 다 원래 안 보이므로 다른 칸 5개가 그대로
        assertEquals(5, GaugeLayout.DEFAULT.gridCells(GaugeMetric.WEATHER, recording = false).size)
    }

    @Test fun roundTripAndOrder() {
        val l = GaugeLayout.DEFAULT.moved(6, 0).toggled(GaugeCell.CLOCK, false)
        val back = GaugeLayout.decode(l.encode())
        assertEquals(l, back)
        assertEquals(GaugeCell.WEATHER, back.entries.first().cell)
        assertEquals(false, back.entries.first { it.cell == GaugeCell.CLOCK }.enabled)
    }

    @Test fun decodeFillsMissingAndDropsUnknown() {
        val l = GaugeLayout.decode("WEATHER:1,FOO:1,WEATHER:0,DISTANCE:0")
        assertEquals(GaugeCell.entries.size, l.entries.size)
        assertEquals(GaugeCell.WEATHER, l.entries[0].cell)
        assertEquals(true, l.entries[0].enabled)
        assertEquals(false, l.entries[1].enabled)
    }

    @Test fun cannotTurnOffLastOne() {
        var l = GaugeLayout.DEFAULT
        GaugeCell.entries.forEach { l = l.toggled(it, false) }
        assertEquals(1, l.enabledCells.size)
    }
}
