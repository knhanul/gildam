package kr.co.nuni.gildam.data.merge

import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.local.entity.TrackPointEntity
import kr.co.nuni.gildam.data.model.ActivityStatus
import kr.co.nuni.gildam.data.model.SyncState
import kr.co.nuni.gildam.data.model.TransportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordMergerTest {

    private fun activity(
        id: String,
        start: Long,
        distance: Double = 1000.0,
        durationMs: Long = 600_000L,
        synced: SyncState = SyncState.LOCAL_ONLY
    ) = ActivityEntity(
        id = id,
        title = "기록 $id",
        transportType = TransportType.WALK,
        status = ActivityStatus.COMPLETED,
        startedAtEpochMs = start,
        endedAtEpochMs = start + durationMs,
        totalDurationMs = durationMs,
        movingDurationMs = durationMs,
        distanceMeters = distance,
        maxSpeedMps = 2f,
        elevationGainMeters = 10f,
        syncState = synced
    )

    private fun points(activityId: String, count: Int, start: Long) = List(count) { i ->
        TrackPointEntity(
            activityId = activityId,
            seq = i + 1,
            latitude = 37.5 + i * 0.001,
            longitude = 127.0,
            recordedAtEpochMs = start + i * 1000L
        )
    }

    @Test
    fun `두 기록을 합치면 시간과 거리를 더하고 경로를 순서대로 잇는다`() {
        val a = activity("a", 1_000L, 1000.0, 600_000L)
        val b = activity("b", 2_000_000L, 500.0, 300_000L)
        val result = RecordMerger.merge(
            sources = listOf(a, b),
            pointsByActivity = mapOf("a" to points("a", 3, 1_000L), "b" to points("b", 2, 2_000_000L)),
            newId = "merged",
            title = "합친 기록",
            now = 9_000_000L
        )

        assertEquals(1500.0, result.activity.distanceMeters, 0.001)
        assertEquals(900_000L, result.activity.totalDurationMs)
        assertEquals(20f, result.activity.elevationGainMeters, 0.001f)
        assertEquals(5, result.points.size)
        assertEquals(listOf(1, 2, 3, 4, 5), result.points.map { it.seq })
        assertTrue(result.points.all { it.activityId == "merged" })
        assertEquals(1_000L, result.activity.startedAtEpochMs)
        assertEquals(2_300_000L, result.activity.endedAtEpochMs)
    }

    @Test
    fun `두 번째 기록의 첫 점은 끊김 표시가 붙어 곧은 선으로 이어지지 않는다`() {
        val a = activity("a", 1_000L)
        val b = activity("b", 2_000_000L)
        val result = RecordMerger.merge(
            sources = listOf(a, b),
            pointsByActivity = mapOf("a" to points("a", 2, 1_000L), "b" to points("b", 3, 2_000_000L)),
            newId = "merged", title = "합침", now = 9_000_000L
        )

        assertEquals(false, result.points[0].isPauseBoundary)
        assertEquals(true, result.points[2].isPauseBoundary)
        assertEquals(false, result.points[3].isPauseBoundary)
    }

    @Test
    fun `기기에만 있던 기록은 바로 지우고 서버에 올린 기록은 삭제 대기열로 넘긴다`() {
        val local = activity("local", 1_000L, synced = SyncState.LOCAL_ONLY)
        val cloud = activity("cloud", 2_000L, synced = SyncState.SYNCED)
        val result = RecordMerger.merge(
            sources = listOf(local, cloud),
            pointsByActivity = mapOf("local" to points("local", 1, 1_000L), "cloud" to points("cloud", 1, 2_000L)),
            newId = "merged", title = "합침", now = 9_000_000L
        )

        assertEquals(listOf("cloud"), result.serverDeleteIds)
        assertEquals(listOf("local"), result.localDeleteIds)
        assertEquals(SyncState.PENDING_UPLOAD, result.activity.syncState)
    }

    @Test
    fun `기기에만 있는 기록끼리 합치면 합친 기록도 기기에만 남는다`() {
        val result = RecordMerger.merge(
            sources = listOf(activity("a", 1_000L), activity("b", 2_000L)),
            pointsByActivity = mapOf("a" to points("a", 1, 1_000L), "b" to points("b", 1, 2_000L)),
            newId = "merged", title = "합침", now = 9_000_000L
        )
        assertEquals(SyncState.LOCAL_ONLY, result.activity.syncState)
        assertTrue(result.serverDeleteIds.isEmpty())
    }

    @Test
    fun `기록이 하나거나 끝나지 않았으면 합칠 수 없다`() {
        assertEquals("need at least 2 records", RecordMerger.validate(listOf(activity("a", 1_000L))))
        val recording = activity("a", 1_000L).copy(status = ActivityStatus.RECORDING)
        assertEquals(
            "only completed records",
            RecordMerger.validate(listOf(recording, activity("b", 2_000L)))
        )
        assertNull(RecordMerger.validate(listOf(activity("a", 1_000L), activity("b", 2_000L))))
    }

    @Test
    fun `합친 기록의 평균 속도는 거리 나누기 시간으로 다시 계산한다`() {
        val a = activity("a", 1_000L, distance = 1000.0, durationMs = 1000_000L)
        val b = activity("b", 2_000_000L, distance = 3000.0, durationMs = 1000_000L)
        val result = RecordMerger.merge(
            sources = listOf(a, b),
            pointsByActivity = emptyMap(),
            newId = "m", title = "t", now = 9_000_000L
        )
        assertEquals(2.0f, result.activity.avgSpeedMps, 0.001f)
        assertEquals(2.0f, result.activity.movingAvgSpeedMps, 0.001f)
    }
}
