package kr.co.nuni.gildam.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kr.co.nuni.gildam.data.model.ActivityStatus
import kr.co.nuni.gildam.data.model.PhotoBackupState
import kr.co.nuni.gildam.data.model.SyncState
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.data.model.UriStatus
import kr.co.nuni.gildam.data.model.Visibility

@Entity(
    tableName = "activities",
    indices = [
        Index(value = ["started_at"]),
        Index(value = ["sync_state"]),
        Index(value = ["transport_type"])
    ]
)
data class ActivityEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "user_id")
    val userId: String? = null,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "transport_type")
    val transportType: TransportType = TransportType.WALK,
    @ColumnInfo(name = "status")
    val status: ActivityStatus = ActivityStatus.RECORDING,
    @ColumnInfo(name = "started_at")
    val startedAtEpochMs: Long,
    @ColumnInfo(name = "ended_at")
    val endedAtEpochMs: Long? = null,
    @ColumnInfo(name = "total_duration_ms")
    val totalDurationMs: Long = 0L,
    @ColumnInfo(name = "moving_duration_ms")
    val movingDurationMs: Long = 0L,
    @ColumnInfo(name = "distance_m")
    val distanceMeters: Double = 0.0,
    @ColumnInfo(name = "avg_speed_mps")
    val avgSpeedMps: Float = 0f,
    @ColumnInfo(name = "moving_avg_speed_mps")
    val movingAvgSpeedMps: Float = 0f,
    @ColumnInfo(name = "max_speed_mps")
    val maxSpeedMps: Float = 0f,
    @ColumnInfo(name = "elevation_gain_m")
    val elevationGainMeters: Float = 0f,
    @ColumnInfo(name = "mini_polyline")
    val miniPolyline: String? = null,
    @ColumnInfo(name = "visibility")
    val visibility: Visibility = Visibility.PRIVATE,
    @ColumnInfo(name = "sync_state")
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    @ColumnInfo(name = "points_synced")
    val pointsSynced: Boolean = false,
    @ColumnInfo(name = "version")
    val version: Int = 1,
    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "deleted_at")
    val deletedAtEpochMs: Long? = null
)

@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["activity_id", "seq"])
    ]
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "activity_id")
    val activityId: String,
    @ColumnInfo(name = "seq")
    val seq: Int,
    @ColumnInfo(name = "lat")
    val latitude: Double,
    @ColumnInfo(name = "lon")
    val longitude: Double,
    @ColumnInfo(name = "ele")
    val altitudeMeters: Float? = null,
    @ColumnInfo(name = "speed_mps")
    val speedMps: Float? = null,
    @ColumnInfo(name = "bearing")
    val bearing: Float? = null,
    @ColumnInfo(name = "horizontal_accuracy_m")
    val horizontalAccuracyMeters: Float = 0f,
    @ColumnInfo(name = "recorded_at")
    val recordedAtEpochMs: Long,
    @ColumnInfo(name = "is_pause_boundary")
    val isPauseBoundary: Boolean = false
)

@Entity(
    tableName = "waypoints",
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["activity_id"]),
        Index(value = ["sync_state"])
    ]
)
data class WaypointEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "activity_id")
    val activityId: String,
    @ColumnInfo(name = "lat")
    val latitude: Double,
    @ColumnInfo(name = "lon")
    val longitude: Double,
    @ColumnInfo(name = "ele")
    val altitudeMeters: Float? = null,
    @ColumnInfo(name = "recorded_at")
    val recordedAtEpochMs: Long,
    @ColumnInfo(name = "memo_text")
    val memoText: String,
    @ColumnInfo(name = "caption")
    val caption: String? = null,
    @ColumnInfo(name = "has_photo")
    val hasPhoto: Boolean = false,
    @ColumnInfo(name = "photo_local_only")
    val photoLocalOnly: Boolean = true,
    @ColumnInfo(name = "photo_id")
    val photoId: String? = null,
    @ColumnInfo(name = "captured_device_id")
    val capturedDeviceIdHash: String? = null,
    @ColumnInfo(name = "captured_device_name")
    val capturedDeviceName: String? = null,
    @ColumnInfo(name = "sync_state")
    val syncState: SyncState = SyncState.LOCAL_ONLY,
    @ColumnInfo(name = "version")
    val version: Int = 1,
    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "deleted_at")
    val deletedAtEpochMs: Long? = null
)

/**
 * 로컬 전용 사진 테이블.
 * 이 엔티티의 mediaStoreUri 및 thumbnailLocalPath는 절대 OVH 서버로 전송되지 않습니다.
 */
@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = WaypointEntity::class,
            parentColumns = ["id"],
            childColumns = ["waypoint_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["waypoint_id"], unique = true)
    ]
)
data class PhotoEntity(
    @PrimaryKey
    @ColumnInfo(name = "photo_id")
    val photoId: String,
    @ColumnInfo(name = "waypoint_id")
    val waypointId: String,
    @ColumnInfo(name = "media_store_uri")
    val mediaStoreUri: String,
    @ColumnInfo(name = "media_store_id")
    val mediaStoreId: Long? = null,
    @ColumnInfo(name = "thumbnail_local_path")
    val thumbnailLocalPath: String? = null,
    @ColumnInfo(name = "caption")
    val caption: String? = null,
    @ColumnInfo(name = "captured_at")
    val capturedAtEpochMs: Long,
    @ColumnInfo(name = "lat")
    val latitude: Double,
    @ColumnInfo(name = "lon")
    val longitude: Double,
    @ColumnInfo(name = "captured_device_name")
    val capturedDeviceName: String,
    @ColumnInfo(name = "uri_status")
    val uriStatus: UriStatus = UriStatus.VALID,
    @ColumnInfo(name = "backup_state")
    val backupState: PhotoBackupState = PhotoBackupState.LOCAL_ONLY
)
