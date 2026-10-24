package kr.co.nuni.gildam.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class WaypointWithPhoto(
    @Embedded
    val waypoint: WaypointEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "waypoint_id"
    )
    val localPhoto: PhotoEntity?
)

data class ActivityDetailBundle(
    @Embedded
    val activity: ActivityEntity,
    @Relation(
        entity = WaypointEntity::class,
        parentColumn = "id",
        entityColumn = "activity_id"
    )
    val waypoints: List<WaypointWithPhoto>
)
