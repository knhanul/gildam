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
    /** 내리막 누적(m). 스키마 6 이전 기록은 null → 저장된 경로 고도로 한 번 계산 가능하면 채우고, 아니면 null('–') */
    @ColumnInfo(name = "elevation_loss_m")
    val elevationLossMeters: Float? = null,
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

/**
 * 기록이 일어난 지역 이름(기기 Geocoder 로 좌표를 거꾸로 찾아 저장).
 * - region_name: 지역별 묶어 보기용('시/도 + 시·군·구'). null 이면 한 번 시도했지만 못 얻은 기록.
 * - start_place / end_place: 목록 행 표시용 세밀 지명(한국은 동·읍·면까지, 도로명·번지 제외).
 *   places_resolved_at 이 null 이면 아직 출발·도착 지명을 찾지 않은 상태(스키마 4→5 기존 행 포함)라 백필 대상입니다.
 */
@Entity(
    tableName = "activity_regions",
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activity_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ActivityRegionEntity(
    @PrimaryKey
    @ColumnInfo(name = "activity_id")
    val activityId: String,
    @ColumnInfo(name = "region_name")
    val regionName: String? = null,
    @ColumnInfo(name = "resolved_at")
    val resolvedAtEpochMs: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "start_place")
    val startPlace: String? = null,
    @ColumnInfo(name = "end_place")
    val endPlace: String? = null,
    @ColumnInfo(name = "places_resolved_at")
    val placesResolvedAtEpochMs: Long? = null
)
