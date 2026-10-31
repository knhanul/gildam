package kr.co.nuni.gildam.data.course

import kr.co.nuni.gildam.data.gpxcompare.GpxPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseFollowerRestoreTest {
    private val line = listOf(GpxPoint(37.50, 127.00, null), GpxPoint(37.50, 127.01, null))

    @Test fun updateRemembersProgress() {
        val f = CourseFollower(line)
        assertNull(f.lastAlongM)
        f.update(37.50, 127.005)
        assertTrue(f.lastAlongM!! in 300.0..600.0)
        assertEquals(0.0, f.lastOffsetM!!, 1e-6)
    }

    @Test fun restoreStateBringsBackProgressAndOffCourse() {
        val f = CourseFollower(line)
        f.restoreState(alongM = 420.0, offsetM = 61.0, offCourse = true)
        assertEquals(420.0, f.lastAlongM!!, 1e-9)
        assertEquals(61.0, f.lastOffsetM!!, 1e-9)
        assertTrue(f.isOffCourse)
        // 복원 직후 같은 자리에 다시 들어와도 '새로 이탈' 진동은 울리지 않음
        val (_, newlyOff) = f.update(37.50055, 127.004) // 약 61m 남쪽
        assertFalse(newlyOff)
    }
}
