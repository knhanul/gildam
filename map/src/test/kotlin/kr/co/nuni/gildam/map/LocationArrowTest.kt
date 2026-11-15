package kr.co.nuni.gildam.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationArrowTest {

    @Test fun screenRotationIsHeadingWhenMapIsNorthUp() {
        assertEquals(40.0, LocationArrow.screenRotationDeg(40.0, 0.0), 1e-6)
        assertEquals(270.0, LocationArrow.screenRotationDeg(270.0, 0.0), 1e-6)
    }

    @Test fun screenRotationPointsUpWhenMapIsHeadingUp() {
        assertEquals(0.0, LocationArrow.screenRotationDeg(40.0, 40.0), 1e-6)
        assertEquals(0.0, LocationArrow.screenRotationDeg(180.0, 180.0), 1e-6)
        assertEquals(0.0, LocationArrow.screenRotationDeg(0.0, 0.0), 1e-6)
    }

    @Test fun screenRotationWrapsAround() {
        assertEquals(20.0, LocationArrow.screenRotationDeg(10.0, 350.0), 1e-6)
        assertEquals(340.0, LocationArrow.screenRotationDeg(350.0, 10.0), 1e-6)
        assertEquals(270.0, LocationArrow.screenRotationDeg(0.0, 90.0), 1e-6)
    }

    @Test fun chevronPointsUpWithNotchBehind() {
        val pts = LocationArrow.chevronPoints(100f)
        assertEquals(4, pts.size)
        val tip = pts[0]
        val right = pts[1]
        val notch = pts[2]
        val left = pts[3]
        // 끝은 가운데보다 위(작은 y), 홈은 끝보다 아래, 날개는 홈보다 아래
        assertTrue(tip.second < 50f)
        assertTrue(notch.second > tip.second)
        assertTrue(right.second > notch.second)
        assertTrue(left.second > notch.second)
        // 좌우 대칭, 끝과 홈은 가운데
        assertEquals(50f, tip.first, 0.01f)
        assertEquals(50f, notch.first, 0.01f)
        assertEquals(right.first - 50f, 50f - left.first, 0.01f)
    }
}
