package kr.co.nuni.gildam.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kr.co.nuni.gildam.data.local.entity.ActivityDetailBundle
import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.local.entity.PhotoEntity
import kr.co.nuni.gildam.data.local.entity.TrackPointEntity
import kr.co.nuni.gildam.data.local.entity.WaypointEntity
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivity(activity: ActivityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackPointsBatch(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE activity_id = :activityId ORDER BY seq ASC")
    fun observeTrackPoints(activityId: String): Flow<List<TrackPointEntity>>

    @Query("SELECT * FROM track_points WHERE activity_id = :activityId ORDER BY seq ASC")
    suspend fun getTrackPoints(activityId: String): List<TrackPointEntity>

    @Transaction
    @Query("SELECT * FROM waypoints WHERE activity_id = :activityId AND deleted_at IS NULL ORDER BY recorded_at ASC")
    fun observeWaypointsWithPhoto(activityId: String): Flow<List<WaypointWithPhoto>>

    @Query("SELECT * FROM waypoints WHERE activity_id = :activityId AND deleted_at IS NULL ORDER BY recorded_at ASC")
    suspend fun getWaypointsForActivity(activityId: String): List<WaypointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
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
}
