package kr.co.nuni.gildam.data.repository

import android.net.Uri
import android.os.Build
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kr.co.nuni.gildam.data.elevation.ElevationAccumulator
import kr.co.nuni.gildam.data.list.ActivityPlaceInfo
import kr.co.nuni.gildam.data.local.dao.GildamDao
import kr.co.nuni.gildam.data.local.entity.ActivityDetailBundle
import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.local.entity.ActivityRegionEntity
import kr.co.nuni.gildam.data.local.entity.PhotoEntity
import kr.co.nuni.gildam.data.local.entity.TrackPointEntity
import kr.co.nuni.gildam.data.local.entity.WaypointEntity
import kr.co.nuni.gildam.data.local.entity.WeatherSnapshotEntity
import kr.co.nuni.gildam.data.media.LocalPhotoManager
import kr.co.nuni.gildam.data.merge.RecordMerger
import kr.co.nuni.gildam.data.region.RegionResolver
import kr.co.nuni.gildam.data.model.ActivityStatus
import kr.co.nuni.gildam.data.model.PhotoBackupState
import kr.co.nuni.gildam.data.model.SyncState
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.data.model.UriStatus
import kr.co.nuni.gildam.data.model.Visibility
import kr.co.nuni.gildam.data.weather.WeatherNow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GildamRepository @Inject constructor(
    private val dao: GildamDao,
    private val localPhotoManager: LocalPhotoManager,
    private val regionResolver: RegionResolver
) {
    fun observeAllActivities(): Flow<List<ActivityEntity>> = dao.observeAllActivities()

    fun observeActivityDetail(activityId: String): Flow<ActivityDetailBundle?> =
        dao.observeActivityDetail(activityId)

    fun observeTrackPoints(activityId: String): Flow<List<TrackPointEntity>> =
        dao.observeTrackPoints(activityId)

    suspend fun getTrackPoints(activityId: String): List<TrackPointEntity> =
        dao.getTrackPoints(activityId)

    /** 기록 id → 경로 점 개수 (점이 하나도 없는 기록은 빠짐) */
    suspend fun trackPointCounts(): Map<String, Int> =
        dao.countTrackPointsByActivity().associate { it.activityId to it.pointCount }

    suspend fun getActivityById(activityId: String): ActivityEntity? =
        dao.getActivityById(activityId)

    suspend fun getMaxTrackPointSeq(activityId: String): Int =
        dao.getMaxTrackPointSeq(activityId)

    suspend fun deleteTrackPointsAfter(activityId: String, cutoffMs: Long) {
        dao.deleteTrackPointsAfter(activityId, cutoffMs)
    }

    /**
     * 강제 종료 등으로 남아 있던 미완료 기록을 '여기서 마치기'로 완료 처리합니다.
     * 종료 시각은 마지막 저장 점의 시각으로 하고, 거리·시간은 저장된 값을 그대로 사용합니다.
     */
    suspend fun finishUnfinishedRecording(activityId: String): Boolean {
        val existing = dao.getActivityById(activityId) ?: return false
        val points = dao.getTrackPoints(activityId)
        val endedAt = points.lastOrNull()?.recordedAtEpochMs
            ?: existing.endedAtEpochMs
            ?: System.currentTimeMillis()
        val backfilledLoss = existing.elevationLossMeters
            ?: ElevationAccumulator.computeFromTrackPoints(points)?.second
        updateRecordingProgress(
            activityId = existing.id,
            status = ActivityStatus.COMPLETED,
            totalDurationMs = existing.totalDurationMs,
            movingDurationMs = existing.movingDurationMs,
            distanceMeters = existing.distanceMeters,
            avgSpeedMps = existing.avgSpeedMps,
            movingAvgSpeedMps = existing.movingAvgSpeedMps,
            maxSpeedMps = existing.maxSpeedMps,
            elevationGainMeters = existing.elevationGainMeters,
            elevationLossMeters = backfilledLoss,
            endedAtEpochMs = endedAt
        )
        runCatching { resolveActivityPlaces(existing.id) }
        return true
    }

    suspend fun getWaypoints(activityId: String): List<WaypointEntity> =
        dao.getWaypointsForActivity(activityId)

    suspend fun getActiveRecording(): ActivityEntity? = dao.getActiveRecordingActivity()

    suspend fun getWeatherSnapshot(activityId: String): WeatherSnapshotEntity? =
        dao.getWeatherSnapshot(activityId)

    /** 기록 시작 때의 날씨만 한 번 저장합니다. 이미 있으면 덮어쓰지 않습니다. */
    suspend fun saveWeatherSnapshotIfAbsent(activityId: String, weather: WeatherNow) {
        if (dao.getWeatherSnapshot(activityId) != null) return
        dao.upsertWeatherSnapshot(WeatherSnapshotEntity.from(activityId, weather))
    }

    suspend fun createNewRecording(
        title: String,
        transportType: TransportType,
        currentUserId: String?
    ): ActivityEntity {
        val now = System.currentTimeMillis()
        val entity = ActivityEntity(
            id = UUID.randomUUID().toString(),
            userId = currentUserId,
            title = title,
            transportType = transportType,
            status = ActivityStatus.RECORDING,
            startedAtEpochMs = now,
            visibility = Visibility.PRIVATE,
            syncState = if (currentUserId != null) SyncState.PENDING_UPLOAD else SyncState.LOCAL_ONLY,
            updatedAtEpochMs = now
        )
        dao.upsertActivity(entity)
        return entity
    }

    suspend fun updateRecordingProgress(
        activityId: String,
        status: ActivityStatus,
        totalDurationMs: Long,
        movingDurationMs: Long,
        distanceMeters: Double,
        avgSpeedMps: Float,
        movingAvgSpeedMps: Float,
        maxSpeedMps: Float,
        elevationGainMeters: Float,
        elevationLossMeters: Float? = null,
        endedAtEpochMs: Long? = null
    ) {
        val existing = dao.getActivityById(activityId) ?: return
        val now = System.currentTimeMillis()
        dao.upsertActivity(
            existing.copy(
                status = status,
                endedAtEpochMs = if (status == ActivityStatus.COMPLETED) (endedAtEpochMs ?: now) else existing.endedAtEpochMs,
                totalDurationMs = totalDurationMs,
                movingDurationMs = movingDurationMs,
                distanceMeters = distanceMeters,
                avgSpeedMps = avgSpeedMps,
                movingAvgSpeedMps = movingAvgSpeedMps,
                maxSpeedMps = maxSpeedMps,
                elevationGainMeters = elevationGainMeters,
                elevationLossMeters = elevationLossMeters ?: existing.elevationLossMeters,
                syncState = if (existing.userId != null) SyncState.PENDING_UPLOAD else SyncState.LOCAL_ONLY,
                updatedAtEpochMs = now
            )
        )
    }

    suspend fun insertTrackPointsBatch(points: List<TrackPointEntity>) {
        if (points.isNotEmpty()) {
            dao.insertTrackPointsBatch(points)
        }
    }

    /**
     * 길 위의 메모·사진 추가:
     * - 사진은 앱 폴더로 복사하지 않고 MediaStore URI만 PhotoEntity에 기록합니다.
     * - 로컬 전용 썸네일만 filesDir/thumbnails에 생성합니다.
     * - 동기화 대상인 WaypointEntity에는 photoLocalOnly = true, photoId, 촬영 기기명만 남깁니다.
     */
    suspend fun addWaypointWithOptionalPhoto(
        activityId: String,
        latitude: Double,
        longitude: Double,
        altitudeMeters: Float?,
        memoText: String,
        caption: String?,
        mediaStoreUri: Uri?,
        isLoggedIn: Boolean
    ): WaypointEntity {
        val now = System.currentTimeMillis()
        val waypointId = UUID.randomUUID().toString()
        val photoId = if (mediaStoreUri != null) "ph_${UUID.randomUUID()}" else null
        val deviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim()

        val waypoint = WaypointEntity(
            id = waypointId,
            activityId = activityId,
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = altitudeMeters,
            recordedAtEpochMs = now,
            memoText = memoText,
            caption = caption,
            hasPhoto = mediaStoreUri != null,
            photoLocalOnly = true,
            photoId = photoId,
            capturedDeviceIdHash = Integer.toHexString(deviceName.hashCode()),
            capturedDeviceName = if (mediaStoreUri != null) deviceName else null,
            syncState = if (isLoggedIn) SyncState.PENDING_UPLOAD else SyncState.LOCAL_ONLY,
            updatedAtEpochMs = now
        )
        dao.upsertWaypoint(waypoint)

        if (mediaStoreUri != null && photoId != null) {
            val localThumbPath = localPhotoManager.generateLocalThumbnail(photoId, mediaStoreUri)
            val photoEntity = PhotoEntity(
                photoId = photoId,
                waypointId = waypointId,
                mediaStoreUri = mediaStoreUri.toString(),
                thumbnailLocalPath = localThumbPath,
                caption = caption,
                capturedAtEpochMs = now,
                latitude = latitude,
                longitude = longitude,
                capturedDeviceName = deviceName,
                uriStatus = UriStatus.VALID,
                backupState = PhotoBackupState.LOCAL_ONLY
            )
            dao.upsertPhoto(photoEntity)
        }
        return waypoint
    }

    /**
     * 상세 화면 진입 시 로컬 사진들의 MediaStore URI가 깨졌는지(갤러리에서 삭제되었는지) 점검합니다.
     */
    suspend fun verifyPhotosForActivity(bundle: ActivityDetailBundle) {
        for (item in bundle.waypoints) {
            val photo = item.localPhoto ?: continue
            val newStatus = localPhotoManager.verifyMediaStoreUri(photo.mediaStoreUri)
            if (newStatus != photo.uriStatus) {
                dao.updatePhotoUriStatus(photo.photoId, newStatus)
            }
        }
    }

    suspend fun relinkMissingPhoto(photoId: String, newMediaStoreUri: Uri) {
        val newThumb = localPhotoManager.generateLocalThumbnail(photoId, newMediaStoreUri)
        dao.relinkPhotoUri(photoId, newMediaStoreUri.toString(), newThumb)
    }

    suspend fun removeMissingPhotoKeepMemo(waypointId: String, photoId: String) {
        dao.deleteLocalPhotoEntry(photoId)
        dao.clearWaypointPhotoFlag(waypointId)
    }

    suspend fun updateActivityTitleAndVisibility(
        activityId: String,
        newTitle: String,
        newVisibility: Visibility
    ) {
        val existing = dao.getActivityById(activityId) ?: return
        dao.upsertActivity(
            existing.copy(
                title = newTitle,
                visibility = newVisibility,
                syncState = if (existing.syncState == SyncState.LOCAL_ONLY) SyncState.LOCAL_ONLY else SyncState.PENDING_UPLOAD,
                version = existing.version + 1,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    /** 제목·활동 구분 수정. 바뀐 게 있으면 true */
    suspend fun updateTitleAndTransport(activityId: String, title: String, type: TransportType): Boolean {
        val existing = dao.getActivityById(activityId) ?: return false
        val edited = RecordEditPolicy.applyEdit(existing, title, type, System.currentTimeMillis()) ?: return false
        if (edited == existing) return false
        dao.upsertActivity(edited)
        return true
    }

    /** 기록 삭제. 서버 삭제가 필요하면 true (워커를 깨워야 함) */
    suspend fun deleteActivity(activityId: String): Boolean {
        val existing = dao.getActivityById(activityId) ?: return false
        return when (RecordEditPolicy.deleteAction(existing)) {
            RecordEditPolicy.DeleteAction.HARD_DELETE_NOW -> { dao.hardDeleteActivity(activityId); false }
            RecordEditPolicy.DeleteAction.QUEUE_SERVER_DELETE -> { dao.upsertActivity(RecordEditPolicy.markDeleted(existing, System.currentTimeMillis())); true }
        }
    }

    /** 여러 기록 일괄 삭제(짧은 기록 모아서 지우기 등). 하나라도 서버 삭제 대기열에 들어갔으면 true */
    suspend fun deleteActivities(activityIds: List<String>): Boolean {
        var needsSync = false
        for (id in activityIds) {
            if (deleteActivity(id)) needsSync = true
        }
        return needsSync
    }

    /** '클라우드에 올리기' 요청. 대기열에 넣었으면 true */
    suspend fun requestCloudUpload(activityId: String): Boolean {
        val existing = dao.getActivityById(activityId) ?: return false
        val queued = RecordEditPolicy.requestUpload(existing) ?: return false
        dao.setActivitySyncStateOnly(activityId, queued.syncState)
        return true
    }

    /**
     * 여러 길기록을 하나로 합칩니다.
     * - 경로 점·길 메모(사진 참조)는 새 기록으로 옮기고, 원본 기록은 지웁니다(서버에 올린 기록은 삭제 대기열).
     * - 사진 파일은 기기에 그대로 두고 참조만 새 기록 쪽으로 옮깁니다.
     * @return 새 기록 id, 실패하면 null
     */
    suspend fun mergeActivities(activityIds: List<String>, title: String? = null): String? {
        val now = System.currentTimeMillis()
        val sources = activityIds.mapNotNull { dao.getActivityById(it) }
            .filter { it.deletedAtEpochMs == null }
            .sortedBy { it.startedAtEpochMs }
        RecordMerger.validate(sources)?.let { return null }

        val pointsByActivity = sources.associate { it.id to dao.getTrackPoints(it.id) }
        val newId = UUID.randomUUID().toString()
        val finalTitle = title?.let { RecordEditPolicy.cleanTitle(it) } ?: RecordMerger.defaultTitle(sources)
        val merged = RecordMerger.merge(sources, pointsByActivity, newId, finalTitle, now)

        // 길 메모는 id 를 유지한 채 활동 id 만 새 기록으로 바꿉니다 (사진 참조가 그대로 살아 있음)
        val movedWaypoints = sources.flatMap { source ->
            dao.getAllWaypointsForActivity(source.id)
                .filter { it.deletedAtEpochMs == null }
                .map { it.copy(activityId = newId, syncState = merged.activity.syncState) }
        }

        dao.saveMergedActivity(merged.activity, merged.points, movedWaypoints)

        // 원본 기록 정리: 기기에만 있던 기록은 바로 지우고, 서버에 올린 적 있는 기록은 삭제 대기열에 넣습니다
        merged.localDeleteIds.forEach { dao.hardDeleteActivity(it) }
        merged.serverDeleteIds.forEach { id ->
            dao.getActivityById(id)?.let { dao.upsertActivity(RecordEditPolicy.markDeleted(it, now)) }
        }
        runCatching { resolveActivityPlaces(newId) }
        return newId
    }

    // ---- 지역 이름 (지역별 묶어 보기 + 출발·도착 지명) ----

    /** activity_id → 지역 이름(null 이면 '지역 정보 없음') */
    fun observeActivityRegions(): Flow<Map<String, String?>> =
        dao.observeActivityRegions().map { list -> list.associate { it.activityId to it.regionName } }

    /** activity_id → 지역·출발/도착 지명 정보 */
    fun observeActivityPlaces(): Flow<Map<String, ActivityPlaceInfo>> =
        dao.observeActivityRegions().map { list ->
            list.associate { entity ->
                entity.activityId to ActivityPlaceInfo(
                    regionName = entity.regionName,
                    startPlace = entity.startPlace,
                    endPlace = entity.endPlace,
                    placesResolved = entity.placesResolvedAtEpochMs != null
                )
            }
        }

    /**
     * 한 기록의 지역 이름(기존 값이 있으면 보존)과 출발·도착 지명을 기기 Geocoder 로 찾아 저장합니다.
     * - 출발 = 첫 경로 점, 도착 = 마지막 경로 점.
     * - 경로 점이 없으면 첫 길 메모 좌표로 출발만 시도하고 도착은 비웁니다.
     * - 못 찾아도 places_resolved_at 을 기록해 같은 기록을 무한 재시도하지 않습니다.
     */
    suspend fun resolveActivityPlaces(activityId: String) {
        val existing = dao.getActivityRegion(activityId)
        val firstPoint = dao.getFirstTrackPoint(activityId)
        val lastPoint = dao.getLastTrackPoint(activityId)

        var regionName: String? = existing?.regionName
        val startPlace: String?
        val endPlace: String?

        if (firstPoint != null) {
            val firstResolved = regionResolver.resolveLocation(firstPoint.latitude, firstPoint.longitude)
            if (existing == null) regionName = firstResolved?.regionName
            startPlace = firstResolved?.placeName

            endPlace = if (lastPoint == null) {
                null
            } else if (lastPoint.id == firstPoint.id ||
                (lastPoint.latitude == firstPoint.latitude && lastPoint.longitude == firstPoint.longitude)
            ) {
                startPlace
            } else {
                regionResolver.resolvePlace(lastPoint.latitude, lastPoint.longitude)
            }
        } else {
            val firstWpt = dao.getWaypointsForActivity(activityId).firstOrNull()
            val wptResolved = firstWpt?.let { regionResolver.resolveLocation(it.latitude, it.longitude) }
            if (existing == null) regionName = wptResolved?.regionName
            startPlace = wptResolved?.placeName
            endPlace = null
        }

        val now = System.currentTimeMillis()
        dao.upsertActivityRegion(
            ActivityRegionEntity(
                activityId = activityId,
                regionName = regionName,
                resolvedAtEpochMs = existing?.resolvedAtEpochMs ?: now,
                startPlace = startPlace,
                endPlace = endPlace,
                placesResolvedAtEpochMs = now
            )
        )
    }

    /**
     * 지역 이름이나 출발·도착 지명이 아직 없는 완료 기록 몇 건을 기기 Geocoder 로 찾아 저장합니다.
     * - 기존 스키마 4 행(region_name 만 있고 places_resolved_at == null)은 기존 region_name 을 유지한 채
     *   start_place / end_place 만 백필합니다.
     * - 기록 사이 200ms 쉬어 기기를 혹사하지 않습니다.
     * @return 이번에 새로 채운 기록 수
     */
    suspend fun resolveMissingRegions(limit: Int = 8): Int {
        val targets = dao.getActivitiesMissingRegion(limit)
        if (targets.isEmpty()) return 0
        var resolved = 0
        for (activity in targets) {
            resolveActivityPlaces(activity.id)
            resolved += 1
            delay(200L)
        }
        return resolved
    }

    /**
     * 스키마 6 이전 기록(elevation_loss_m == null)의 저장된 경로 점 고도로 오르막·내리막을 한 번 계산해 채웁니다.
     * 고도 정보가 없어 계산할 수 없는 기록은 -1f 센티넬을 기록해 매번 다시 읽지 않게 합니다(화면에서는 음수·null을 '–' 처리).
     */
    suspend fun backfillMissingElevationLoss(limit: Int = 10): Int {
        val targets = dao.getActivitiesMissingElevationLoss(limit)
        if (targets.isEmpty()) return 0
        for (activity in targets) {
            val points = dao.getTrackPoints(activity.id)
            val computed = ElevationAccumulator.computeFromTrackPoints(points)
            val gain = if (activity.elevationGainMeters > 0f) activity.elevationGainMeters else (computed?.first ?: 0f)
            val loss = computed?.second ?: -1f
            dao.updateActivityElevationOnly(activity.id, gain, loss)
        }
        return targets.size
    }

    suspend fun ensureDemoSampleIfEmpty() {
        if (dao.getActivityCount() > 0) return
        val now = System.currentTimeMillis()
        val sampleId = "sample-bukaksan-0192b8"
        val sampleActivity = ActivityEntity(
            id = sampleId,
            userId = null,
            title = "북악산 스카이웨이 새벽 걸음",
            transportType = TransportType.WALK,
            status = ActivityStatus.COMPLETED,
            startedAtEpochMs = now - 7_200_000L,
            endedAtEpochMs = now - 1_070_000L,
            totalDurationMs = 6_130_000L,
            movingDurationMs = 5_520_000L,
            distanceMeters = 6450.0,
            avgSpeedMps = 1.05f,
            movingAvgSpeedMps = 1.17f,
            maxSpeedMps = 1.85f,
            elevationGainMeters = 248f,
            elevationLossMeters = 235f,
            visibility = Visibility.PRIVATE,
            syncState = SyncState.SYNCED,
            pointsSynced = true,
            updatedAtEpochMs = now
        )
        dao.upsertActivity(sampleActivity)

        val sampleCoords = listOf(
            Triple(37.5881, 126.9710, 142f),
            Triple(37.5902, 126.9728, 178f),
            Triple(37.5921, 126.9742, 210f),
            Triple(37.5955, 126.9781, 268f),
            Triple(37.5988, 126.9810, 312f),
            Triple(37.5940, 126.9835, 235f),
            Triple(37.5890, 126.9855, 155f)
        )
        val points = sampleCoords.mapIndexed { idx, (lat, lon, ele) ->
            TrackPointEntity(
                activityId = sampleId,
                seq = idx + 1,
                latitude = lat,
                longitude = lon,
                altitudeMeters = ele,
                speedMps = 1.15f,
                horizontalAccuracyMeters = 4.5f,
                recordedAtEpochMs = (now - 7_200_000L) + (idx * 900_000L)
            )
        }
        dao.insertTrackPointsBatch(points)

        // 케이스 B 시연: 다른 기기(Galaxy S24 Ultra)에서 촬영되어 현재 기기에는 사진이 없는 Waypoint
        dao.upsertWaypoint(
            WaypointEntity(
                id = "wpt-sample-remote-device",
                activityId = sampleId,
                latitude = 37.5988,
                longitude = 126.9810,
                altitudeMeters = 312f,
                recordedAtEpochMs = now - 3_600_000L,
                memoText = "전망대 벤치에서 따뜻한 커피 한 모금. 단풍이 막 시작됐다.",
                caption = "북악 팔각정 전망대",
                hasPhoto = true,
                photoLocalOnly = true,
                photoId = "ph-remote-only-01",
                capturedDeviceIdHash = "a1b2c3d4",
                capturedDeviceName = "Galaxy S24 Ultra",
                syncState = SyncState.SYNCED
            )
        )

        // 케이스 C 시연: 이 기기에서 찍었으나 갤러리에서 원본 사진이 삭제된 상태
        val missingPhotoWptId = "wpt-sample-missing-uri"
        val missingPhotoId = "ph-sample-missing-uri"
        dao.upsertWaypoint(
            WaypointEntity(
                id = missingPhotoWptId,
                activityId = sampleId,
                latitude = 37.5890,
                longitude = 126.9855,
                altitudeMeters = 155f,
                recordedAtEpochMs = now - 1_800_000L,
                memoText = "나무 계단이 이슬에 젖어 조금 미끄러웠다. 메모는 사진이 지워져도 그대로 남는다.",
                caption = "하산길 숲길 계단",
                hasPhoto = true,
                photoLocalOnly = true,
                photoId = missingPhotoId,
                capturedDeviceIdHash = "e5f60718",
                capturedDeviceName = "이 기기",
                syncState = SyncState.LOCAL_ONLY
            )
        )
        dao.upsertPhoto(
            PhotoEntity(
                photoId = missingPhotoId,
                waypointId = missingPhotoWptId,
                mediaStoreUri = "content://media/external/images/media/999999999",
                thumbnailLocalPath = null,
                caption = "하산길 숲길 계단",
                capturedAtEpochMs = now - 1_800_000L,
                latitude = 37.5890,
                longitude = 126.9855,
                capturedDeviceName = "이 기기",
                uriStatus = UriStatus.MISSING_IN_GALLERY,
                backupState = PhotoBackupState.LOCAL_ONLY
            )
        )
    }
}
