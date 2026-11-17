package kr.co.nuni.gildam.data.list

import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.model.ActivityStatus
import kr.co.nuni.gildam.data.model.TransportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordListFilterTest {

    private fun rec(
        id: String,
        title: String = "기본 제목",
        distanceM: Double = 5000.0,
        durationMs: Long = 1_800_000L,
        type: TransportType = TransportType.BIKE,
        status: ActivityStatus = ActivityStatus.COMPLETED
    ) = ActivityEntity(
        id = id,
        title = title,
        transportType = type,
        status = status,
        startedAtEpochMs = 1_700_000_000_000L,
        endedAtEpochMs = 1_700_000_000_000L + durationMs,
        totalDurationMs = durationMs,
        movingDurationMs = durationMs,
        distanceMeters = distanceM
    )

    @Test
    fun `검색어는 제목뿐 아니라 출발·도착 지명과 지역 이름도 찾는다`() {
        val commute = rec("a1", title = "11월 17일 07:08 자전거 길이야기")
        val places = mapOf(
            "a1" to ActivityPlaceInfo(
                regionName = "경기 수원시 영통구",
                startPlace = "매탄동",
                endPlace = "역삼동",
                placesResolved = true
            )
        )

        assertTrue(RecordListFilter.matchesQuery(commute, places["a1"], "매탄동"))
        assertTrue(RecordListFilter.matchesQuery(commute, places["a1"], "역삼"))
        assertTrue(RecordListFilter.matchesQuery(commute, places["a1"], "수원시"))
        assertTrue(RecordListFilter.matchesQuery(commute, places["a1"], "자전거"))
        assertFalse(RecordListFilter.matchesQuery(commute, places["a1"], "종로구"))
    }

    @Test
    fun `짧은 기록 판별은 완료된 기록 중 거리 0_1km 미만 또는 시간 1분 미만이다`() {
        assertTrue(RecordListFilter.isShortRecord(rec("s1", distanceM = 99.9, durationMs = 120_000L)))
        assertTrue(RecordListFilter.isShortRecord(rec("s2", distanceM = 500.0, durationMs = 59_999L)))
        assertFalse(RecordListFilter.isShortRecord(rec("ok", distanceM = 100.0, durationMs = 60_000L)))
        // 진행 중인 기록은 짧은 기록 삭제 대상이 아니다
        assertFalse(
            RecordListFilter.isShortRecord(
                rec("live", distanceM = 10.0, durationMs = 5_000L, status = ActivityStatus.RECORDING)
            )
        )
    }

    @Test
    fun `짧은 기록 숨기기가 꺼져 있으면 모두 보이고 켜면 숨김 목록으로 분리된다`() {
        val list = listOf(
            rec("normal", distanceM = 19_710.0, durationMs = 4_856_000L),
            rec("zeroDist", distanceM = 0.0, durationMs = 12_000L),
            rec("shortTime", distanceM = 250.0, durationMs = 45_000L)
        )

        val offResult = RecordListFilter.filter(list, emptyMap(), null, "", hideShort = false)
        assertEquals(listOf("normal", "zeroDist", "shortTime"), offResult.visible.map { it.id })
        assertTrue(offResult.hiddenShort.isEmpty())

        val onResult = RecordListFilter.filter(list, emptyMap(), null, "", hideShort = true)
        assertEquals(listOf("normal"), onResult.visible.map { it.id })
        assertEquals(listOf("zeroDist", "shortTime"), onResult.hiddenShort.map { it.id })
    }
}
