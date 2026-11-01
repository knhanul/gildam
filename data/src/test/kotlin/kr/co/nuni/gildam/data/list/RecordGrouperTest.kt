package kr.co.nuni.gildam.data.list

import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.model.ActivityStatus
import kr.co.nuni.gildam.data.model.TransportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RecordGrouperTest {

    private val zone = ZoneId.of("Asia/Seoul")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 9): Long =
        LocalDateTime.of(year, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun activity(id: String, startedAt: Long) = ActivityEntity(
        id = id,
        title = "기록 $id",
        transportType = TransportType.WALK,
        status = ActivityStatus.COMPLETED,
        startedAtEpochMs = startedAt,
        endedAtEpochMs = startedAt + 600_000L,
        totalDurationMs = 600_000L,
        movingDurationMs = 500_000L,
        distanceMeters = 1000.0
    )

    @Test
    fun `최근순은 들어온 순서 그대로 한 묶음이고 헤더가 없다`() {
        val list = listOf(
            activity("a", at(2026, 11, 1)),
            activity("b", at(2026, 9, 20)),
            activity("c", at(2025, 12, 31))
        )
        val groups = RecordGrouper.group(list, emptyMap(), RecordGroupMode.RECENT, zone)

        assertEquals(1, groups.size)
        assertEquals("", groups[0].title)
        assertEquals(listOf("a", "b", "c"), groups[0].activities.map { it.id })
    }

    @Test
    fun `월별은 최신 달이 위로 오고 그룹 안은 최신순이다`() {
        val list = listOf(
            activity("nov2", at(2026, 11, 20)),
            activity("sep", at(2026, 9, 20)),
            activity("nov1", at(2026, 11, 2)),
            activity("lastYear", at(2025, 12, 31))
        )
        val groups = RecordGrouper.group(list, emptyMap(), RecordGroupMode.MONTH, zone)

        assertEquals(listOf("2026년 11월", "2026년 9월", "2025년 12월"), groups.map { it.title })
        assertEquals(listOf("2026-11", "2026-09", "2025-12"), groups.map { it.key })
        assertEquals(listOf("nov2", "nov1"), groups[0].activities.map { it.id })
        assertEquals(listOf("sep"), groups[1].activities.map { it.id })
    }

    @Test
    fun `월별은 시간대에 따라 달이 갈린다`() {
        // 한국 시간 2026-11-01 00:30 은 UTC 로는 2026-10-31 15:30 → 한국 기준으로 묶여야 함
        val id = activity("edge", at(2026, 11, 1, hour = 0))
        val groups = RecordGrouper.group(listOf(id), emptyMap(), RecordGroupMode.MONTH, zone)
        assertEquals("2026년 11월", groups.single().title)
    }

    @Test
    fun `지역별은 이름 가나다순으로 묶고 그룹 안은 최신순이다`() {
        val list = listOf(
            activity("s2", at(2026, 11, 5)),
            activity("g1", at(2026, 10, 1)),
            activity("s1", at(2026, 9, 1)),
            activity("j1", at(2026, 8, 1))
        )
        val regions = mapOf(
            "s1" to "서울 종로구",
            "s2" to "서울 종로구",
            "g1" to "경기 성남시 분당구",
            "j1" to "제주 제주시"
        )
        val groups = RecordGrouper.group(list, regions, RecordGroupMode.REGION, zone)

        // 가나다순: 경기 → 서울 → 제주
        assertEquals(listOf("경기 성남시 분당구", "서울 종로구", "제주 제주시"), groups.map { it.title })
        assertEquals(listOf("s2", "s1"), groups[1].activities.map { it.id })
    }

    @Test
    fun `지역을 모르는 기록은 확인 중과 정보 없음으로 갈라 맨 아래에 둔다`() {
        val list = listOf(
            activity("known", at(2026, 11, 5)),
            activity("pending", at(2026, 11, 4)),
            activity("none", at(2026, 11, 3))
        )
        // pending 은 regions 에 키 자체가 없음, none 은 키는 있고 값이 null
        val regions = mapOf<String, String?>("known" to "서울 종로구", "none" to null)
        val groups = RecordGrouper.group(list, regions, RecordGroupMode.REGION, zone)

        assertEquals(3, groups.size)
        assertEquals("서울 종로구", groups[0].title)
        assertEquals("지역 정보 없음", groups[1].title)
        assertEquals(listOf("none"), groups[1].activities.map { it.id })
        assertEquals("지역 확인 중", groups[2].title)
        assertEquals(listOf("pending"), groups[2].activities.map { it.id })
    }

    @Test
    fun `빈 이름은 정보 없음으로 본다`() {
        val list = listOf(activity("blank", at(2026, 11, 1)))
        val groups = RecordGrouper.group(list, mapOf("blank" to "   "), RecordGroupMode.REGION, zone)
        assertEquals("지역 정보 없음", groups.single().title)
    }

    @Test
    fun `기록이 없으면 월별과 지역별 모두 빈 목록이다`() {
        assertTrue(RecordGrouper.group(emptyList(), emptyMap(), RecordGroupMode.MONTH, zone).isEmpty())
        assertTrue(RecordGrouper.group(emptyList(), emptyMap(), RecordGroupMode.REGION, zone).isEmpty())
        assertEquals(1, RecordGrouper.group(emptyList(), emptyMap(), RecordGroupMode.RECENT, zone).size)
    }
}
