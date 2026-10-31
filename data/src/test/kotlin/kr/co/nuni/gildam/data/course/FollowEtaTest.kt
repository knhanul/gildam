package kr.co.nuni.gildam.data.course

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowEtaTest {
    @Test fun etaUsesRecordingAverageSpeed() {
        // 3 km/h = 0.8333 m/s, 남은 1500m → 1800초 (30분)
        val speed = FollowEta.usableSpeedMps(movingAvgSpeedMps = 3f / 3.6f, distanceMeters = 900.0, elapsedMovingMs = 1_080_000)
        assertEquals(0.8333, speed!!, 1e-3)
        assertEquals(1800.0, FollowEta.etaSeconds(1500.0, speed, fallbackSec = 9999.0), 1e-3)
        assertTrue(FollowEta.usesRecordingSpeed(speed, 1500.0))
    }

    @Test fun derivedSpeedUsedWhenStateValueIsZero() {
        // 상태 값이 0 이어도 900m / 18분 = 0.833 m/s 로 계산
        val speed = FollowEta.usableSpeedMps(0f, distanceMeters = 900.0, elapsedMovingMs = 1_080_000)
        assertEquals(0.8333, speed!!, 1e-3)
    }

    @Test fun tooSlowOrNoMovingTimeFallsBackToModel() {
        assertNull(FollowEta.usableSpeedMps(0.1f, distanceMeters = 5.0, elapsedMovingMs = 0))
        assertNull(FollowEta.usableSpeedMps(0.2f, distanceMeters = 20.0, elapsedMovingMs = 600_000)) // 0.033 m/s
        val eta = FollowEta.etaSeconds(2_000.0, speedMps = null, fallbackSec = 2_400.0)
        assertEquals(2_400.0, eta, 1e-9)
        assertFalse(FollowEta.usesRecordingSpeed(null, 2_000.0))
    }

    @Test fun unreasonableSpeedIsRejected() {
        assertNull(FollowEta.usableSpeedMps(45f, 10_000.0, 200_000)) // 45 m/s = GPS 튐
    }

    @Test fun veryLongEtaFallsBackAndIsClampedToADay() {
        // 0.31 m/s 로 40km 남음 → 36시간 → 모델 값 사용
        val eta = FollowEta.etaSeconds(40_000.0, 0.31, fallbackSec = 7_200.0)
        assertEquals(7_200.0, eta, 1e-9)
        // 모델 값마저 하루를 넘으면 24시간으로 자름
        assertEquals(FollowEta.MAX_ETA_SEC, FollowEta.etaSeconds(40_000.0, 0.31, fallbackSec = 200_000.0), 1e-9)
        // 모델 값도 없으면 0 (터무니없는 값 대신 빈 시간 대신 0초 → '1분' 표시)
        assertEquals(0.0, FollowEta.etaSeconds(40_000.0, 0.31, fallbackSec = Double.NaN), 1e-9)
    }

    @Test fun zeroRemainingIsZero() {
        assertEquals(0.0, FollowEta.etaSeconds(0.0, 1.4, 5_000.0), 1e-9)
        assertFalse(FollowEta.usesRecordingSpeed(1.4, 0.0))
    }
}
