package kr.co.nuni.gildam.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import kr.co.nuni.gildam.data.course.CourseDao
import kr.co.nuni.gildam.data.course.CourseEntity
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kr.co.nuni.gildam.data.local.dao.GildamDao
import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.local.entity.PhotoEntity
import kr.co.nuni.gildam.data.local.entity.TrackPointEntity
import kr.co.nuni.gildam.data.local.entity.WaypointEntity
import kr.co.nuni.gildam.data.model.ActivityStatus
import kr.co.nuni.gildam.data.model.PhotoBackupState
import kr.co.nuni.gildam.data.model.SyncState
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.data.model.UriStatus
import kr.co.nuni.gildam.data.model.Visibility

class GildamTypeConverters {
    @TypeConverter fun fromTransport(v: TransportType): String = v.name
    @TypeConverter fun toTransport(v: String): TransportType = TransportType.valueOf(v)

    @TypeConverter fun fromStatus(v: ActivityStatus): String = v.name
    @TypeConverter fun toStatus(v: String): ActivityStatus = ActivityStatus.valueOf(v)

    @TypeConverter fun fromSyncState(v: SyncState): String = v.name
    @TypeConverter fun toSyncState(v: String): SyncState = SyncState.valueOf(v)

    @TypeConverter fun fromVisibility(v: Visibility): String = v.name
    @TypeConverter fun toVisibility(v: String): Visibility = Visibility.valueOf(v)

    @TypeConverter fun fromPhotoBackupState(v: PhotoBackupState): String = v.name
    @TypeConverter fun toPhotoBackupState(v: String): PhotoBackupState = PhotoBackupState.valueOf(v)

    @TypeConverter fun fromUriStatus(v: UriStatus): String = v.name
    @TypeConverter fun toUriStatus(v: String): UriStatus = UriStatus.valueOf(v)
}

@Database(
    entities = [
        ActivityEntity::class,
        TrackPointEntity::class,
        WaypointEntity::class,
        PhotoEntity::class,
        CourseEntity::class
    ],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true
)
@TypeConverters(GildamTypeConverters::class)
abstract class GildamDatabase : RoomDatabase() {
    abstract fun gildamDao(): GildamDao
    abstract fun courseDao(): CourseDao
}
