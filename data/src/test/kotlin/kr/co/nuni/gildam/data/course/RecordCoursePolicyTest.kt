package kr.co.nuni.gildam.data.course

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordCoursePolicyTest {
    @Test fun noPoints() = assertEquals(RecordCourseBlock.NO_POINTS, RecordCoursePolicy.check(0, 500.0))
    @Test fun onePointIsTooShort() = assertEquals(RecordCourseBlock.TOO_SHORT, RecordCoursePolicy.check(1, 500.0))
    @Test fun under100mIsTooShort() = assertEquals(RecordCourseBlock.TOO_SHORT, RecordCoursePolicy.check(40, 99.0))
    @Test fun unfinishedFirst() = assertEquals(RecordCourseBlock.NOT_FINISHED, RecordCoursePolicy.check(0, 0.0, finished = false))
    @Test fun okRecord() = assertNull(RecordCoursePolicy.check(2, 100.0))
}
