package kr.co.nuni.gildam.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kr.co.nuni.gildam.data.local.entity.ActivityDetailBundle
import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.local.entity.ActivityRegionEntity
import kr.co.nuni.gildam.data.local.entity.PhotoEntity
import kr.co.nuni.gildam.data.local.entity.TrackPointEntity
import kr.co.nuni.gildam.data.local.entity.WaypointEntity
import kr.co.nuni.gildam.data.local.entity.WeatherSnapshotEntity
import kr.co.nuni.gildam.data.local.entity.WaypointWithPhoto
import kr.co.nuni.gildam.data.model.ActivityStatus
import kr.co.nuni.gildam.data.model.SyncState
import kr.co.nuni.gildam.data.model.UriStatus

@Dao
interface GildamDao {

    @Query("SELECT * FROM activities WHERE deleted_at IS NULL ORDER BY started_at DESC")
    fun observeAllActivities(): Flow<List<ActivityEntity>>

    @Query("SELECT COUNT(*) FROM activities")
    suspend fun getActivityCount(): Int

    @Query("SELECT * FROM activities WHERE status IN ('RECORDING', 'PAUSED') ORDER BY started_at DESC LIMIT 1")
    suspend fun getActiveRecordingActivity(): ActivityEntity?

    @Transaction
    @Query("SELECT * FROM activities WHERE id = :activityId AND deleted_at IS NULL")
    fun observeActivityDetail(activityId: String): Flow<ActivityDetailBundle?>

    @Query("SELECT * FROM activities WHERE id = :activityId")
    suspend fun getActivityById(activityId: String): ActivityEntity?

    // 주의: REPLACE 는 행을 지웠다 다시 넣어서 CASCADE 로 경로 점·위치 메모가 모두 지워짐 → @Upsert(갱신) 사용
    @Upsert
    suspend fun upsertActivity(activity: ActivityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackPointsBatch(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE activity_id = :activityId ORDER BY seq ASC")
    fun observeTrackPoints(activityId: String): Flow<List<TrackPointEntity>>

    @Query("SELECT * FROM track_points WHERE activity_id = :activityId ORDER BY seq ASC")
    suspend fun getTrackPoints(activityId: String): List<TrackPointEntity>

    /** 지역 이름을 찾을 때 쓰는 첫 경로 점 (전체 점을 읽지 않도록 하나만) */
    @Query("SELECT * FROM track_points WHERE activity_id = :activityId ORDER BY seq ASC LIMIT 1")
    suspend fun getFirstTrackPoint(activityId: String): TrackPointEntity?

    /** 도착 지명을 찾을 때 쓰는 마지막 경로 점 (전체 점을 읽지 않도록 하나만) */
    @Query("SELECT * FROM track_points WHERE activity_id = :activityId ORDER BY seq DESC LIMIT 1")
    suspend fun getLastTrackPoint(activityId: String): TrackPointEntity?

    @Query("SELECT COALESCE(MAX(seq), 0) FROM track_points WHERE activity_id = :activityId")
    suspend fun getMaxTrackPointSeq(activityId: String): Int

    @Query("DELETE FROM track_points WHERE activity_id = :activityId AND recorded_at > :cutoffMs")
    suspend fun deleteTrackPointsAfter(activityId: String, cutoffMs: Long)

    @Transaction
    @Query("SELECT * FROM waypoints WHERE activity_id = :activityId AND deleted_at IS NULL ORDER BY recorded_at ASC")
    fun observeWaypointsWithPhoto(activityId: String): Flow<List<WaypointWithPhoto>>

    @Query("SELECT * FROM waypoints WHERE activity_id = :activityId AND deleted_at IS NULL ORDER BY recorded_at ASC")
    suspend fun getWaypointsForActivity(activityId: String): List<WaypointEntity>

    // REPLACE 면 사진 행이 CASCADE 로 지워짐 → @Upsert
    @Upsert
    suspend fun upsertWaypoint(waypoint: WaypointEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhoto(photo: PhotoEntity)

    @Query("UPDATE photos SET uri_status = :status WHERE photo_id = :photoId")
    suspend fun updatePhotoUriStatus(photoId: String, status: UriStatus)

    @Query("UPDATE photos SET media_store_uri = :newUri, thumbnail_local_path = :newThumbPath, uri_status = 'VALID' WHERE photo_id = :photoId")
    suspend fun relinkPhotoUri(photoId: String, newUri: String, newThumbPath: String?)

    @Query("DELETE FROM photos WHERE photo_id = :photoId")
    suspend fun deleteLocalPhotoEntry(photoId: String)

    @Query("UPDATE waypoints SET has_photo = 0, photo_id = NULL, updated_at = :now WHERE id = :waypointId")
    suspend fun clearWaypointPhotoFlag(waypointId: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM activities WHERE status = 'COMPLETED' AND sync_state IN ('LOCAL_ONLY', 'PENDING_UPLOAD', 'SYNC_ERROR')")
    suspend fun getActivitiesPendingSync(): List<ActivityEntity>

    @Query("SELECT * FROM waypoints WHERE sync_state IN ('LOCAL_ONLY', 'PENDING_UPLOAD', 'SYNC_ERROR')")
    suspend fun getWaypointsPendingSync(): List<WaypointEntity>

    @Query("UPDATE activities SET sync_state = :syncState, points_synced = :pointsSynced, updated_at = :now WHERE id = :activityId")
    suspend fun updateActivitySyncState(
        activityId: String,
        syncState: SyncState,
        pointsSynced: Boolean,
        now: Long = System.currentTimeMillis()
    )

    @Query("UPDATE waypoints SET sync_state = :syncState, updated_at = :now WHERE id = :waypointId")
    suspend fun updateWaypointSyncState(
        waypointId: String,
        syncState: SyncState,
        now: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM activities WHERE sync_state = 'SYNCED'")
    suspend fun deleteAllSyncedLocalDataOnLogout()

    @Query("DELETE FROM activities")
    suspend fun wipeAllLocalData()

    // ---- gildam-api 동기화 (updated_at 을 건드리지 않는 전용 쿼리) ----

    /** LOCAL_ONLY 는 사용자가 '클라우드에 올리기'를 눌러야만 PENDING_UPLOAD 가 됨. SYNCING 은 중단된 업로드 재시도 */
    @Query("SELECT * FROM activities WHERE status = 'COMPLETED' AND deleted_at IS NULL AND sync_state IN ('PENDING_UPLOAD', 'SYNCING', 'SYNC_ERROR')")
    suspend fun getActivitiesToUpload(): List<ActivityEntity>

    @Query("SELECT * FROM activities WHERE sync_state = 'PENDING_DELETE' OR (deleted_at IS NOT NULL AND sync_state NOT IN ('SYNCED', 'LOCAL_ONLY'))")
    suspend fun getActivitiesToDelete(): List<ActivityEntity>

    /** 업로드한 시점 이후 로컬에서 바뀌지 않았을 때만 SYNCED 로 표시 */
    @Query("UPDATE activities SET sync_state = 'SYNCED', points_synced = 1 WHERE id = :activityId AND updated_at = :uploadedUpdatedAt")
    suspend fun markActivitySyncedIfUnchanged(activityId: String, uploadedUpdatedAt: Long): Int

    @Query("UPDATE activities SET sync_state = :syncState WHERE id = :activityId")
    suspend fun setActivitySyncStateOnly(activityId: String, syncState: SyncState)

    @Query("UPDATE waypoints SET sync_state = 'SYNCED' WHERE activity_id = :activityId AND deleted_at IS NULL")
    suspend fun markWaypointsSyncedForActivity(activityId: String)

    @Query("DELETE FROM track_points WHERE activity_id = :activityId")
    suspend fun deleteTrackPoints(activityId: String)

    @Query("SELECT * FROM waypoints WHERE activity_id = :activityId")
    suspend fun getAllWaypointsForActivity(activityId: String): List<WaypointEntity>

    @Query("DELETE FROM waypoints WHERE activity_id = :activityId AND id NOT IN (:keepIds)")
    suspend fun deleteWaypointsNotIn(activityId: String, keepIds: List<String>)

    // ---- 기록 지역 이름 (지역별 묶어 보기) ----

    @Query("SELECT * FROM activity_regions")
    fun observeActivityRegions(): Flow<List<ActivityRegionEntity>>

    @Query("SELECT * FROM activity_regions")
    suspend fun getAllActivityRegions(): List<ActivityRegionEntity>

    @Query("SELECT * FROM activity_regions WHERE activity_id = :activityId")
    suspend fun getActivityRegion(activityId: String): ActivityRegionEntity?

    @Upsert
    suspend fun upsertActivityRegion(region: ActivityRegionEntity)

    /** 지역 행이 아직 없거나 출발·도착 지명(places_resolved_at)을 아직 채우지 않은 완료 기록 (최신순) */
    @Query(
        """
        SELECT a.* FROM activities a
        LEFT JOIN activity_regions r ON r.activity_id = a.id
        WHERE (r.activity_id IS NULL OR r.places_resolved_at IS NULL)
          AND a.deleted_at IS NULL
          AND a.status = 'COMPLETED'
        ORDER BY a.started_at DESC
        LIMIT :limit
        """
    )
    suspend fun getActivitiesMissingRegion(limit: Int): List<ActivityEntity>

    @Query("SELECT * FROM weather_snapshots WHERE activity_id = :activityId")
    suspend fun getWeatherSnapshot(activityId: String): WeatherSnapshotEntity?

    @Upsert
    suspend fun upsertWeatherSnapshot(snapshot: WeatherSnapshotEntity)

    @Query("SELECT * FROM activities WHERE status = 'COMPLETED' AND deleted_at IS NULL AND elevation_loss_m IS NULL ORDER BY started_at DESC LIMIT :limit")
    suspend fun getActivitiesMissingElevationLoss(limit: Int): List<ActivityEntity>

    /** 동기화 updated_at 을 건드리지 않고 로컬 오르막·내리막만 백필합니다(못 계산한 경우 -1f 로 표시해 재시도 방지). */
    @Query("UPDATE activities SET elevation_gain_m = :gainM, elevation_loss_m = :lossM WHERE id = :activityId")
    suspend fun updateActivityElevationOnly(activityId: String, gainM: Float, lossM: Float)

    @Query("DELETE FROM activities WHERE id = :activityId")
    suspend fun hardDeleteActivity(activityId: String)

    /** 합친 기록 저장: 새 기록 + 경로 점 + 옮겨 온 길 메모를 한 트랜잭션으로 씀 */
    @Transaction
    suspend fun saveMergedActivity(
        activity: ActivityEntity,
        points: List<TrackPointEntity>,
        movedWaypoints: List<WaypointEntity>
    ) {
        upsertActivity(activity)
        if (points.isNotEmpty()) points.chunked(5000).forEach { insertTrackPointsBatch(it) }
        movedWaypoints.forEach { upsertWaypoint(it) }
    }

    /** 서버 트랙 한 건을 로컬에 통째로 반영 (트랙포인트 교체, 웨이포인트 병합) */
    @Transaction
    suspend fun replaceActivityFromServer(activity: ActivityEntity, points: List<TrackPointEntity>, waypoints: List<WaypointEntity>) {
        upsertActivity(activity)
        deleteTrackPoints(activity.id)
        if (points.isNotEmpty()) points.chunked(5000).forEach { insertTrackPointsBatch(it) }
        deleteWaypointsNotIn(activity.id, waypoints.map { it.id }.ifEmpty { listOf("") })
        waypoints.forEach { upsertWaypoint(it) }
    }
}
