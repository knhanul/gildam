package kr.co.nuni.gildam.sync

import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.local.entity.TrackPointEntity
import kr.co.nuni.gildam.data.local.entity.WaypointEntity
import kr.co.nuni.gildam.data.model.ActivityStatus
import kr.co.nuni.gildam.data.model.SyncState
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.sync.api.TrackDto
import kr.co.nuni.gildam.sync.api.TrackPointDto
import kr.co.nuni.gildam.sync.api.WaypointDto
import kotlin.math.roundToLong

/** 로컬 Room 엔티티 ↔ gildam-api 트랙 JSON 변환과 동기화 판단 (순수 Kotlin, 단위 테스트 대상). */
object TrackSyncMapper {
    val ID_RE = Regex("^[A-Za-z0-9_-]{8,64}$")
    const val MAX_POINTS = 200_000

    fun isValidId(id: String) = ID_RE.matches(id)

    fun toDto(a: ActivityEntity, points: List<TrackPointEntity>, waypoints: List<WaypointEntity>): TrackDto = TrackDto(
        updatedAt = a.updatedAtEpochMs,
        title = a.title.take(200),
        memo = null,
        activityType = a.transportType.name,
        startedAt = a.startedAtEpochMs,
        endedAt = a.endedAtEpochMs,
        distanceM = a.distanceMeters.coerceAtLeast(0.0),
        durationS = (a.totalDurationMs / 1000.0).coerceAtLeast(0.0),
        elevationGainM = a.elevationGainMeters.toDouble().coerceAtLeast(0.0),
        points = points.sortedBy { it.seq }.take(MAX_POINTS).map {
            TrackPointDto(
                lat = it.latitude, lon = it.longitude, t = it.recordedAtEpochMs,
                ele = it.altitudeMeters?.toDouble()?.takeIf { e -> e in -1000.0..10000.0 },
                acc = it.horizontalAccuracyMeters.toDouble().takeIf { acc -> acc > 0 },
                spd = it.speedMps?.toDouble()?.takeIf { s -> s >= 0 }
            )
        },
        waypoints = waypoints.filter { it.deletedAtEpochMs == null && isValidId(it.id) }.map {
            WaypointDto(
                id = it.id, lat = it.latitude, lon = it.longitude, t = it.recordedAtEpochMs,
                title = it.caption?.take(200), memo = it.memoText.take(10_000),
                photoLocalOnly = it.hasPhoto, photoCount = if (it.hasPhoto) 1 else 0
            )
        }
    )

    data class LocalTrack(val activity: ActivityEntity, val points: List<TrackPointEntity>, val waypoints: List<WaypointEntity>)

    /**
     * 서버 트랙 → 로컬. 서버에 없는 정보(이동수단 등)는 기존 로컬 값을 유지하고,
     * 사진 연결(photoId 등)은 이 기기 로컬 값만 유지합니다.
     */
    fun fromDto(id: String, dto: TrackDto, existing: ActivityEntity?, existingWaypoints: List<WaypointEntity>, userId: String?): LocalTrack {
        val durationMs = ((dto.durationS ?: 0.0) * 1000).roundToLong()
        val distance = dto.distanceM ?: 0.0
        val activity = ActivityEntity(
            id = id,
            userId = userId ?: existing?.userId,
            title = dto.title ?: existing?.title ?: "길 기록",
            transportType = parseActivityType(dto.activityType) ?: existing?.transportType ?: TransportType.WALK,
            status = ActivityStatus.COMPLETED,
            startedAtEpochMs = dto.startedAt ?: dto.points.firstOrNull()?.t ?: dto.updatedAt,
            endedAtEpochMs = dto.endedAt ?: dto.points.lastOrNull()?.t,
            totalDurationMs = durationMs,
            movingDurationMs = existing?.movingDurationMs ?: durationMs,
            distanceMeters = distance,
            avgSpeedMps = if (durationMs > 0) (distance / (durationMs / 1000.0)).toFloat() else 0f,
            movingAvgSpeedMps = existing?.movingAvgSpeedMps ?: 0f,
            maxSpeedMps = (dto.points.mapNotNull { it.spd }.maxOrNull() ?: 0.0).toFloat(),
            elevationGainMeters = (dto.elevationGainM ?: 0.0).toFloat(),
            miniPolyline = existing?.miniPolyline,
            visibility = existing?.visibility ?: kr.co.nuni.gildam.data.model.Visibility.PRIVATE,
            syncState = SyncState.SYNCED,
            pointsSynced = true,
            version = (existing?.version ?: 0) + 1,
            updatedAtEpochMs = dto.updatedAt,
            deletedAtEpochMs = null
        )
        val points = dto.points.mapIndexed { i, p ->
            TrackPointEntity(
                activityId = id, seq = i, latitude = p.lat, longitude = p.lon,
                altitudeMeters = p.ele?.toFloat(), speedMps = p.spd?.toFloat(),
                horizontalAccuracyMeters = (p.acc ?: 0.0).toFloat(), recordedAtEpochMs = p.t
            )
        }
        val byId = existingWaypoints.associateBy { it.id }
        val waypoints = dto.waypoints.map { w ->
            val local = byId[w.id]
            WaypointEntity(
                id = w.id, activityId = id, latitude = w.lat, longitude = w.lon,
                altitudeMeters = local?.altitudeMeters,
                recordedAtEpochMs = w.t ?: local?.recordedAtEpochMs ?: dto.updatedAt,
                memoText = w.memo ?: "",
                caption = w.title,
                hasPhoto = local?.hasPhoto ?: false,           // 사진은 찍은 기기에만
                photoLocalOnly = true,
                photoId = local?.photoId,
                capturedDeviceIdHash = local?.capturedDeviceIdHash,
                capturedDeviceName = local?.capturedDeviceName,
                syncState = SyncState.SYNCED,
                version = (local?.version ?: 0) + 1,
                updatedAtEpochMs = dto.updatedAt
            )
        }
        return LocalTrack(activity, points, waypoints)
    }

    fun parseActivityType(raw: String?): TransportType? =
        raw?.trim()?.uppercase()?.let { v -> TransportType.entries.firstOrNull { it.name == v } }

    enum class RemoteAction { APPLY, SKIP_LOCAL_NEWER, DELETE_LOCAL, IGNORE }

    /** 서버 변경 1건을 로컬에 어떻게 반영할지 결정 (updated_at 이 큰 쪽이 이김). */
    fun decide(local: ActivityEntity?, remoteUpdatedAt: Long, remoteDeleted: Boolean): RemoteAction {
        val localPending = local != null && local.syncState != SyncState.SYNCED
        return when {
            remoteDeleted && local == null -> RemoteAction.IGNORE
            remoteDeleted -> if (localPending && local!!.updatedAtEpochMs > remoteUpdatedAt && local.deletedAtEpochMs == null) RemoteAction.SKIP_LOCAL_NEWER else RemoteAction.DELETE_LOCAL
            local == null -> RemoteAction.APPLY
            local.status != ActivityStatus.COMPLETED -> RemoteAction.SKIP_LOCAL_NEWER // 기록 중인 트랙은 건드리지 않음
            localPending && local.updatedAtEpochMs >= remoteUpdatedAt -> RemoteAction.SKIP_LOCAL_NEWER
            !localPending && local.updatedAtEpochMs >= remoteUpdatedAt -> RemoteAction.IGNORE // 이미 반영됨(내가 올린 것)
            else -> RemoteAction.APPLY
        }
    }
}
