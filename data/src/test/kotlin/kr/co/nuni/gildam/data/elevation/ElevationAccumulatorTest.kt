package kr.co.nuni.gildam.data.elevation

import kr.co.nuni.gildam.data.local.entity.TrackPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class ElevationAccumulatorTest {

    @Test
    fun noisyFlatBarometerSeriesStaysNearZero() {
        val acc = ElevationAccumulator(hasBarometer = true)
        acc.updateMoving(true)
        // ±1.2 m 진폭의 잔진동(기압 임계값 2m 미만) 300개 샘플 → 오르막·내리막 모두 0 근처
        for (i in 0 until 300) {
            val alt = 50.0f + (1.2 * sin(i * 0.6)).toFloat()
            acc.onBarometerAltitude(alt, moving = true)
        }
        assertEquals(0f, acc.gainM, 0.5f)
        assertEquals(0f, acc.lossM, 0.5f)
    }

    @Test
    fun noisyFlatGpsSeriesStaysNearZero() {
        val acc = ElevationAccumulator(hasBarometer = false)
        // ±3.5 m 진폭의 GPS 고도 흔들림(GPS 임계값 5m 미만) → 누적 0 근처
        for (i in 0 until 300) {
            val alt = 100.0 + 3.5 * sin(i * 0.5)
            acc.onGpsAltitude(alt, verticalAccuracyM = 8f, moving = true)
        }
        assertEquals(0f, acc.gainM, 0.5f)
        assertEquals(0f, acc.lossM, 0.5f)
    }

    @Test
    fun staircaseAscentAndDescentAccumulateAccurately() {
        val acc = ElevationAccumulator(hasBarometer = true)
        // 10m씩 4계단 상승(+40m) 후 15m씩 2계단 하강(-30m), 각 단에서 충분히 머물러 저역 필터 수렴
        val plateaus = listOf(100f, 110f, 120f, 130f, 140f, 125f, 110f)
        for (level in plateaus) {
            repeat(30) { acc.onBarometerAltitude(level, moving = true) }
        }
        assertEquals(40f, acc.gainM, 0.8f)
        assertEquals(30f, acc.lossM, 0.8f)
    }

    @Test
    fun barometerPriorityIgnoresGpsForGainAndOnlyCalibratesSeaLevel() {
        val acc = ElevationAccumulator(hasBarometer = true)
        assertEquals(ElevationAccumulator.Source.BAROMETER, acc.activeSource)

        repeat(20) { acc.onBarometerAltitude(50f, moving = true) }
        // 기압이 있을 때 GPS 고도가 크게 요동쳐도 gain/loss 에는 반영되지 않고 해발 오프셋만 보정됨
        acc.onGpsAltitude(200.0, verticalAccuracyM = 5f, moving = true)
        acc.onGpsAltitude(260.0, verticalAccuracyM = 5f, moving = true)
        assertEquals(0f, acc.gainM, 0.01f)
        assertEquals(0f, acc.lossM, 0.01f)
        val seaLevel = acc.calibratedSeaLevelM
        assertNotNull(seaLevel)
        assertTrue("보정 해발 $seaLevel", seaLevel!! > 190f)
    }

    @Test
    fun gpsFallbackRejectsBadVerticalAccuracyAndIgnoresBarometerCalls() {
        val acc = ElevationAccumulator(hasBarometer = false)
        assertEquals(ElevationAccumulator.Source.GPS, acc.activeSource)

        // 기압 센서 없는 모드에서는 onBarometerAltitude 호출이 무시됨
        acc.onBarometerAltitude(0f, moving = true)
        acc.onBarometerAltitude(100f, moving = true)
        assertEquals(0f, acc.gainM, 0.001f)

        // 정상 수직 정확도(6m) 기준점 설정 후, 나쁜 수직 정확도(45m > 25m)는 무시됨
        repeat(15) { acc.onGpsAltitude(50.0, verticalAccuracyM = 6f, moving = true) }
        repeat(15) { acc.onGpsAltitude(90.0, verticalAccuracyM = 45f, moving = true) }
        assertEquals(0f, acc.gainM, 0.01f)

        // 정상 수직 정확도로 +20m 상승하면 정확히 반영됨
        repeat(30) { acc.onGpsAltitude(70.0, verticalAccuracyM = 6f, moving = true) }
        assertEquals(20f, acc.gainM, 0.8f)
    }

    @Test
    fun stationaryDriftDoesNotAccumulateAndTracksReference() {
        val acc = ElevationAccumulator(hasBarometer = true)
        repeat(20) { acc.onBarometerAltitude(100f, moving = true) }

        // 정지 중(moving = false) 기압 변화로 고도가 100m → 115m 로 서서히 떠도 누적되지 않음
        for (step in 1..30) {
            acc.onBarometerAltitude(100f + step * 0.5f, moving = false)
        }
        assertEquals(0f, acc.gainM, 0.01f)
        assertEquals(0f, acc.lossM, 0.01f)

        // 다시 출발(moving = true)해 115m → 125m(+10m) 오르면 정지 중 드리프트(15m)는 빼고 +10m만 더해짐
        repeat(30) { acc.onBarometerAltitude(125f, moving = true) }
        assertEquals(10f, acc.gainM, 0.8f)
    }

    @Test
    fun computeFromTrackPointsBackfillsAndReturnsNullWithoutElevation() {
        val noAlt = (0 until 10).map { i ->
            TrackPointEntity(activityId = "a", seq = i, latitude = 37.5, longitude = 127.0, altitudeMeters = null, recordedAtEpochMs = i * 1000L)
        }
        assertNull(ElevationAccumulator.computeFromTrackPoints(noAlt))

        val hills = buildList {
            var seq = 0
            listOf(30f, 50f, 80f, 60f, 40f).forEach { target ->
                repeat(12) {
                    add(
                        TrackPointEntity(
                            activityId = "b",
                            seq = seq++,
                            latitude = 37.5 + seq * 0.0001,
                            longitude = 127.0,
                            altitudeMeters = target,
                            speedMps = 1.5f,
                            recordedAtEpochMs = seq * 1000L
                        )
                    )
                }
            }
        }
        val pair = ElevationAccumulator.computeFromTrackPoints(hills)
        assertNotNull(pair)
        assertEquals(50f, pair!!.first, 2f)
        assertEquals(40f, pair.second, 2f)
    }
}
