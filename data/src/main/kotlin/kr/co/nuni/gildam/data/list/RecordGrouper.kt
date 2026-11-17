package kr.co.nuni.gildam.data.list

import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.model.TransportType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 기록 목록을 묶어 보는 방식 */
enum class RecordGroupMode(val labelKo: String) {
    /** 날짜별로 묶고 최신순 그대로 */
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
 * - 최근순: 로컬 날짜별로 묶되 최신 날짜가 위 (오늘 / 어제 / 11월 17일 (월)).
 * - 월별: 최신 달이 위. 지역별: 이름 가나다순, 지역을 모르는 기록은 맨 아래.
 */
object RecordGrouper {

    const val UNKNOWN_REGION_TITLE = "지역 확인 중"
    const val NO_REGION_TITLE = "지역 정보 없음"

    fun group(
        activities: List<ActivityEntity>,
        regions: Map<String, String?>,
        mode: RecordGroupMode,
        zoneId: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zoneId)
    ): List<RecordGroup> = when (mode) {
        RecordGroupMode.RECENT -> groupByDate(activities, zoneId, today)
        RecordGroupMode.MONTH -> groupByMonth(activities, zoneId)
        RecordGroupMode.REGION -> groupByRegion(activities, regions)
    }

    private fun groupByDate(
        activities: List<ActivityEntity>,
        zoneId: ZoneId,
        today: LocalDate
    ): List<RecordGroup> =
        activities
            .groupBy { activity ->
                Instant.ofEpochMilli(activity.startedAtEpochMs).atZone(zoneId).toLocalDate()
            }
            .toSortedMap(compareByDescending { it })
            .map { (date, items) ->
                RecordGroup(
                    key = "date:$date",
                    title = formatDateTitleKo(date, today),
                    activities = items
                )
            }

    private fun formatDateTitleKo(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "오늘"
        today.minusDays(1) -> "어제"
        else -> {
            val dow = when (date.dayOfWeek) {
                DayOfWeek.MONDAY -> "월"
                DayOfWeek.TUESDAY -> "화"
                DayOfWeek.WEDNESDAY -> "수"
                DayOfWeek.THURSDAY -> "목"
                DayOfWeek.FRIDAY -> "금"
                DayOfWeek.SATURDAY -> "토"
                DayOfWeek.SUNDAY -> "일"
            }
            if (date.year == today.year) {
                "${date.monthValue}월 ${date.dayOfMonth}일 ($dow)"
            } else {
                "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일 ($dow)"
            }
        }
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

/**
 * 자동 생성된 기본 기록 제목(`default_record_title`, 예: "11월 17일 19:47 자전거 길이야기" / "Nov 17, 19:47 Bike route")인지 판별합니다.
 * 사용자가 직접 고친 제목은 애매하더라도 숨기지 않고, 시작 시각·활동 종류로 만든 전형적인 기본 제목만 true 를 돌려줍니다.
 */
object DefaultRecordTitle {
    private val SPACE_REGEX = Regex("[\\s\\u00A0\\u202F]+")

    private val KO_DATE_TIME_FORMATTERS = listOf(
        DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREAN),
        DateTimeFormatter.ofPattern("M월 d일 H:mm", Locale.KOREAN),
        DateTimeFormatter.ofPattern("M월 d일 a h:mm", Locale.KOREAN)
    )

    private val EN_DATE_TIME_FORMATTERS = listOf(
        DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MMM d, H:mm", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH)
    )

    private val EN_TRANSPORT_LABELS = mapOf(
        TransportType.WALK to "Walk",
        TransportType.RUN to "Run",
        TransportType.BIKE to "Bike",
        TransportType.HIKE to "Hike",
        TransportType.CAR to "Drive"
    )

    fun isDefault(
        title: String,
        startedAtEpochMs: Long,
        transportType: TransportType,
        zoneId: ZoneId = ZoneId.systemDefault(),
        extraCandidates: Collection<String> = emptyList()
    ): Boolean {
        val normalized = normalize(title)
        if (normalized.isEmpty()) return true
        if (extraCandidates.any { normalize(it) == normalized }) return true

        val base = Instant.ofEpochMilli(startedAtEpochMs).atZone(zoneId).toLocalDateTime()
        val koTransport = transportType.labelKo
        val enTransport = EN_TRANSPORT_LABELS.getValue(transportType)

        for (offsetMin in -2L..2L) {
            val ldt = base.plusMinutes(offsetMin)
            for (fmt in KO_DATE_TIME_FORMATTERS) {
                if (normalize("${ldt.format(fmt)} $koTransport 길이야기") == normalized) return true
            }
            for (fmt in EN_DATE_TIME_FORMATTERS) {
                if (normalize("${ldt.format(fmt)} $enTransport route") == normalized) return true
            }
        }
        return false
    }

    fun customTitleOrNull(
        activity: ActivityEntity,
        zoneId: ZoneId = ZoneId.systemDefault(),
        extraCandidates: Collection<String> = emptyList()
    ): String? = activity.title.trim().takeIf {
        it.isNotEmpty() && !isDefault(it, activity.startedAtEpochMs, activity.transportType, zoneId, extraCandidates)
    }

    private fun normalize(raw: String): String = raw.trim().replace(SPACE_REGEX, " ")
}
