package kr.co.nuni.gildam.data.course

/** 기록을 코스로 만들 수 없는 이유 */
enum class RecordCourseBlock {
    /** 끝나지 않은(기록 중·미완료) 기록 */
    NOT_FINISHED,
    /** 경로 점이 하나도 없음 */
    NO_POINTS,
    /** 점이 2개 미만이거나 0.1 km 미만 */
    TOO_SHORT
}

/** 기록 → 코스 가능 여부 (순수 Kotlin) */
object RecordCoursePolicy {
    const val MIN_POINTS = 2
    const val MIN_DISTANCE_M = 100.0

    /** 만들 수 있으면 null, 아니면 이유 */
    fun check(pointCount: Int, distanceM: Double, finished: Boolean = true): RecordCourseBlock? = when {
        !finished -> RecordCourseBlock.NOT_FINISHED
        pointCount <= 0 -> RecordCourseBlock.NO_POINTS
        pointCount < MIN_POINTS || distanceM < MIN_DISTANCE_M -> RecordCourseBlock.TOO_SHORT
        else -> null
    }
}
