package kr.co.nuni.gildam.data.list

import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import java.time.Instant
import java.time.ZoneId

/** 기록 목록을 묶어 보는 방식 */
enum class RecordGroupMode(val labelKo: String) {
    /** 묶지 않고 최신순 그대로 */
    RECENT("최근순"),
    /** '2026년 11월' 처럼 달마다 */
    MONTH("월별"),
    /** '서울 종로구' 처럼 지역마다 */
    REGION("지역별");

    companion object {
        fun fromName(name: String?): RecordGroupMode =
            entries.firstOrNull { it.name == name } ?: RECENT
    }
}

/** 목록에서 한 묶음(헤더 + 그 안의 기록들). [title] 이 비면 헤더를 그리지 않습니다. */
data class RecordGroup(
    val key: String,
    val title: String,
    val activities: List<ActivityEntity>
)

/**
 * 기록 목록을 화면에 그릴 묶음으로 나누는 순수 계산(DB 접근 없음 → 단위 테스트 대상).
 *
 * - 들어오는 목록은 이미 최신순(시작 시각 내림차순)이라고 가정하고, 묶음 안에서도 그 순서를 지킵니다.
 * - 월별: 최신 달이 위. 지역별: 이름 가나다순, 지역을 모르는 기록은 맨 아래.
 */
object RecordGrouper {

    const val UNKNOWN_REGION_TITLE = "지역 확인 중"
    const val NO_REGION_TITLE = "지역 정보 없음"

    fun group(
        activities: List<ActivityEntity>,
        regions: Map<String, String?>,
        mode: RecordGroupMode,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<RecordGroup> = when (mode) {
        RecordGroupMode.RECENT -> listOf(RecordGroup(key = "recent", title = "", activities = activities))
        RecordGroupMode.MONTH -> groupByMonth(activities, zoneId)
        RecordGroupMode.REGION -> groupByRegion(activities, regions)
    }

    private fun groupByMonth(activities: List<ActivityEntity>, zoneId: ZoneId): List<RecordGroup> =
        activities
            .groupBy { activity ->
                val date = Instant.ofEpochMilli(activity.startedAtEpochMs).atZone(zoneId).toLocalDate()
                "${date.year}-%02d".format(date.monthValue)
            }
            .toSortedMap(compareByDescending { it })
            .map { (key, items) ->
                val year = key.substringBefore('-').toInt()
                val month = key.substringAfter('-').toInt()
                RecordGroup(key = key, title = "${year}년 ${month}월", activities = items)
            }

    private fun groupByRegion(activities: List<ActivityEntity>, regions: Map<String, String?>): List<RecordGroup> {
        val named = LinkedHashMap<String, MutableList<ActivityEntity>>()
        val noInfo = mutableListOf<ActivityEntity>()
        val pending = mutableListOf<ActivityEntity>()

        for (activity in activities) {
            if (!regions.containsKey(activity.id)) {
                pending.add(activity)
                continue
            }
            val name = regions[activity.id]?.trim()?.takeIf { it.isNotEmpty() }
            if (name == null) noInfo.add(activity) else named.getOrPut(name) { mutableListOf() }.add(activity)
        }

        val result = named.entries
            .sortedBy { it.key }
            .map { (name, items) -> RecordGroup(key = "region:$name", title = name, activities = items) }

        return result +
            (if (noInfo.isEmpty()) emptyList() else listOf(
                RecordGroup(key = "region:none", title = NO_REGION_TITLE, activities = noInfo)
            )) +
            (if (pending.isEmpty()) emptyList() else listOf(
                RecordGroup(key = "region:pending", title = UNKNOWN_REGION_TITLE, activities = pending)
            ))
    }
}
