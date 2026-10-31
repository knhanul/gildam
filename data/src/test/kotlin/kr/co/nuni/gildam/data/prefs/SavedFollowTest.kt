package kr.co.nuni.gildam.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedFollowTest {
    @Test fun roundTripKeepsCourseAndProgress() {
        val f = SavedFollow("2f8a1c3e-0000-4a4a-9b9b-1234567890ab", 1234.5, 51.25, true)
        assertEquals(f, SavedFollow.decode(f.encode()))
    }

    @Test fun notOffCourseRoundTrip() {
        val f = SavedFollow("course-1", 0.0, 0.0, false)
        assertEquals(f, SavedFollow.decode(f.encode()))
        assertEquals("course-1;0.0;0.0;false", f.encode())
    }

    @Test fun brokenValuesAreIgnored() {
        assertNull(SavedFollow.decode(null))
        assertNull(SavedFollow.decode(""))
        assertNull(SavedFollow.decode("only-id"))
        assertNull(SavedFollow.decode(";abc;0;false"))
        assertNull(SavedFollow.decode("id;NaN?!;0;false"))
        assertNull(SavedFollow.decode("id;10;0;maybe"))
    }

    @Test fun negativeProgressIsClamped() {
        val f = SavedFollow.decode("id;-5.0;-1.0;false")!!
        assertEquals(0.0, f.alongM, 1e-9)
        assertEquals(0.0, f.offsetM, 1e-9)
    }
}
