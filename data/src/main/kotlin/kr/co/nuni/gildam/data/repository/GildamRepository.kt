package kr.co.nuni.gildam.data.repository

import android.net.Uri
import android.os.Build
import kotlinx.coroutines.flow.Flow
import kr.co.nuni.gildam.data.local.dao.GildamDao
import kr.co.nuni.gildam.data.local.entity.ActivityDetailBundle
import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.local.entity.PhotoEntity
import kr.co.nuni.gildam.data.local.entity.TrackPointEntity
import kr.co.nuni.gildam.data.local.entity.WaypointEntity
import kr.co.nuni.gildam.data.media.LocalPhotoManager
import kr.co.nuni.gildam.data.model.ActivityStatus
import kr.co.nuni.gildam.data.model.PhotoBackupState
import kr.co.nuni.gildam.data.model.SyncState
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.data.model.UriStatus
import kr.co.nuni.gildam.data.model.Visibility
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GildamRepository @Inject constructor(
    private val dao: GildamDao,
    private val localPhotoManager: LocalPhotoManager
) {
    fun observeAllActivities(): Flow<List<ActivityEntity>> = dao.observeAllActivities()

    fun observeActivityDetail(activityId: String): Flow<ActivityDetailBundle?> =
        dao.observeActivityDetail(activityId)

    fun observeTrackPoints(activityId: String): Flow<List<TrackPointEntity>> =
        dao.observeTrackPoints(activityId)

    suspend fun getTrackPoints(activityId: String): List<TrackPointEntity> =
        dao.getTrackPoints(activityId)

    suspend fun deleteTrackPointsAfter(activityId: String, cutoffMs: Long) {
        dao.deleteTrackPointsAfter(activityId, cutoffMs)
    }

    suspend fun getWaypoints(activityId: String): List<WaypointEntity> =
        dao.getWaypointsForActivity(activityId)

    suspend fun getActiveRecording(): ActivityEntity? = dao.getActiveRecordingActivity()

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

    /** '클라우드에 올리기' 요청. 대기열에 넣었으면 true */
    suspend fun requestCloudUpload(activityId: String): Boolean {
        val existing = dao.getActivityById(activityId) ?: return false
        val queued = RecordEditPolicy.requestUpload(existing) ?: return false
        dao.setActivitySyncStateOnly(activityId, queued.syncState)
        return true
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
