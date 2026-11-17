package kr.co.nuni.gildam.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedFollowTest {
    @Test fun roundTripKeepsCourseAndProgress() {
        val f = SavedFollow("2f8a1c3e-0000-4a4a-9b9b-1234567890ab", 1234.5, 51.25, true)
        assertEquals(f, SavedFollow.decode(f.encode()))
    }

    @Test fun notOffCourseRoundTrip() {
        val f = SavedFollow("course-1", 0.0, 0.0, false)
        assertEquals(f, SavedFollow.decode(f.encode()))
        assertEquals("course-1;0.0;0.0;false;;;false", f.encode())
    }

    @Test fun reversedRoundTrip() {
        val f = SavedFollow("course-rev", 420.5, 12.0, false, "sess-2", "act-7", reversed = true)
        assertEquals("course-rev;420.5;12.0;false;sess-2;act-7;true", f.encode())
        val decoded = SavedFollow.decode(f.encode())!!
        assertEquals(f, decoded)
        assertTrue(decoded.reversed)
    }

    @Test fun brokenValuesAreIgnored() {
        assertNull(SavedFollow.decode(null))
        assertNull(SavedFollow.decode(""))
        assertNull(SavedFollow.decode("only-id"))
        assertNull(SavedFollow.decode(";abc;0;false"))
        assertNull(SavedFollow.decode("id;NaN?!;0;false"))
        assertNull(SavedFollow.decode("id;10;0;maybe"))
        assertNull(SavedFollow.decode("id;10;0;false;s1;act-1;notbool"))
        assertNull(SavedFollow.decode("id;10;0;false;s1;act-1;true;extra"))
    }

    @Test fun activityIdRoundTripsAndBlankMeansNone() {
        val f = SavedFollow("c1", 10.0, 2.0, false, "s1", "act-9")
        assertEquals("c1;10.0;2.0;false;s1;act-9;false", f.encode())
        assertEquals(f, SavedFollow.decode(f.encode()))
        assertNull(SavedFollow.decode("c1;10.0;2.0;false;s1;;false")!!.activityId)
    }

    @Test fun legacyFourFiveAndSixFieldValuesStillReadAsForward() {
        val four = SavedFollow.decode("c1;10.0;2.0;false")!!
        assertEquals("c1", four.courseId); assertEquals(10.0, four.alongM, 1e-9); assertEquals("", four.sessionId); assertNull(four.activityId); assertFalse(four.reversed)
        val five = SavedFollow.decode("c1;10.0;2.0;true;s1")!!
        assertEquals("s1", five.sessionId); assertNull(five.activityId); assertFalse(five.reversed)
        val six = SavedFollow.decode("c1;10.0;2.0;false;s1;act-9")!!
        assertEquals("act-9", six.activityId); assertFalse(six.reversed)
    }

    @Test fun negativeProgressIsClamped() {
        val f = SavedFollow.decode("id;-5.0;-1.0;false")!!
        assertEquals(0.0, f.alongM, 1e-9)
        assertEquals(0.0, f.offsetM, 1e-9)
    }
}
