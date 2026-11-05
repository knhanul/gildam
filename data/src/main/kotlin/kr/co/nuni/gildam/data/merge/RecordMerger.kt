package kr.co.nuni.gildam.data.merge

import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.local.entity.TrackPointEntity
import kr.co.nuni.gildam.data.model.ActivityStatus
import kr.co.nuni.gildam.data.model.SyncState
import kr.co.nuni.gildam.data.model.Visibility

/**
 * 여러 길기록을 하나로 합치는 순수 계산(DB 접근 없음, 단위 테스트 대상).
 *
 * - 시간·거리·오르막은 기록별 값을 그대로 더합니다(기록 사이의 쉰 시간은 세지 않음).
 * - 경로 점은 출발 시각 순으로 이어 붙이고, 두 번째 기록부터 첫 점에 '끊김 표시'를 달아
 *   지도에서 곧은 선으로 이어지지 않게 합니다.
 * - 합친 기록은 새 id 를 받고, 제목은 사용자가 정한 값을 씁니다.
 */
object RecordMerger {

    /** 합치려면 최소 두 개, 너무 많으면 한 번에 합치지 않음 */
    const val MIN_COUNT = 2
    const val MAX_COUNT = 20

    data class Result(
        val activity: ActivityEntity,
        val points: List<TrackPointEntity>,
        /** 서버에 올린 적이 있어 지우기를 대기열에 넣어야 하는 원본 id */
        val serverDeleteIds: List<String>,
        /** 기기에만 있던 원본 id (바로 지움) */
        val localDeleteIds: List<String>
    )

    fun validate(sources: List<ActivityEntity>): String? = when {
        sources.size < MIN_COUNT -> "need at least $MIN_COUNT records"
        sources.size > MAX_COUNT -> "at most $MAX_COUNT records"
        sources.any { it.status != ActivityStatus.COMPLETED } -> "only completed records"
        else -> null
    }

    /**
     * @param sources 출발 시각 순으로 정렬된 원본 기록들
     * @param pointsByActivity 기록 id → 경로 점(seq 순)
     */
    fun merge(
        sources: List<ActivityEntity>,
        pointsByActivity: Map<String, List<TrackPointEntity>>,
        newId: String,
        title: String,
        now: Long
    ): Result {
        require(sources.size >= MIN_COUNT) { "need at least two records" }

        var totalDurationMs = 0L
        var movingDurationMs = 0L
        var distanceMeters = 0.0
        var maxSpeedMps = 0f
        var elevationGainMeters = 0f

        val mergedPoints = ArrayList<TrackPointEntity>()
        var seq = 0
        var first = true
        for (source in sources) {
            totalDurationMs += source.totalDurationMs.coerceAtLeast(0L)
            movingDurationMs += source.movingDurationMs.coerceAtLeast(0L)
            distanceMeters += source.distanceMeters
            if (source.maxSpeedMps > maxSpeedMps) maxSpeedMps = source.maxSpeedMps
            elevationGainMeters += source.elevationGainMeters

            val pts = pointsByActivity[source.id].orEmpty().sortedBy { it.seq }
            var started = false
            for (p in pts) {
                seq += 1
                // 두 번째 기록부터 첫 점에 '끊김' 표시 → 지도에서 곧은 선으로 잇지 않음
                val boundary = p.isPauseBoundary || (!first && !started)
                started = true
                mergedPoints.add(
                    p.copy(id = 0L, activityId = newId, seq = seq, isPauseBoundary = boundary)
                )
            }
            if (pts.isNotEmpty()) first = false
        }

        val startedAt = sources.minOf { it.startedAtEpochMs }
        val endedAt = sources.maxOf { it.endedAtEpochMs ?: (it.startedAtEpochMs + it.totalDurationMs) }
        val avgSpeedMps = if (totalDurationMs > 1000L) (distanceMeters / (totalDurationMs / 1000.0)).toFloat() else 0f
        val movingAvgSpeedMps =
            if (movingDurationMs > 1000L) (distanceMeters / (movingDurationMs / 1000.0)).toFloat() else 0f

        // 한 번이라도 서버에 올라간 기록(syncState != LOCAL_ONLY)이 있으면 합친 기록도 올려야 합니다
        val anyOnServer = sources.any { it.syncState != SyncState.LOCAL_ONLY }
        val activity = ActivityEntity(
            id = newId,
            userId = sources.firstNotNullOfOrNull { it.userId },
            title = title,
            transportType = sources.first().transportType,
            status = ActivityStatus.COMPLETED,
            startedAtEpochMs = startedAt,
            endedAtEpochMs = endedAt,
            totalDurationMs = totalDurationMs,
            movingDurationMs = movingDurationMs,
            distanceMeters = distanceMeters,
            avgSpeedMps = avgSpeedMps,
            movingAvgSpeedMps = movingAvgSpeedMps,
            maxSpeedMps = maxSpeedMps,
            elevationGainMeters = elevationGainMeters,
            miniPolyline = null,
            visibility = Visibility.PRIVATE,
            // 서버에 올린 적이 있으면 합친 기록도 올려야 하고, 없으면 기기에만 둡니다
            syncState = if (anyOnServer) SyncState.PENDING_UPLOAD else SyncState.LOCAL_ONLY,
            pointsSynced = false,
            version = 1,
            updatedAtEpochMs = now
        )

        // 기기에만 있던 기록은 바로 지우고, 서버에 올린 적 있는 기록은 서버 삭제를 대기열에 넣습니다
        val (serverIds, localIds) = sources.partition { it.syncState != SyncState.LOCAL_ONLY }
        return Result(
            activity = activity,
            points = mergedPoints,
            serverDeleteIds = serverIds.map { it.id },
            localDeleteIds = localIds.map { it.id }
        )
    }

    /** 합칠 때 기본으로 채워 둘 제목 */
    fun defaultTitle(sources: List<ActivityEntity>): String {
        val base = sources.minByOrNull { it.startedAtEpochMs }?.title?.trim().orEmpty()
        return base // 비어 있으면 화면에서 기기 언어로 기본 제목을 채웁니다
    }
}
