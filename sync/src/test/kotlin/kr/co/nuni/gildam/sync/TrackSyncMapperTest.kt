package kr.co.nuni.gildam.sync

import kotlinx.serialization.json.Json
import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.local.entity.TrackPointEntity
import kr.co.nuni.gildam.data.local.entity.WaypointEntity
import kr.co.nuni.gildam.data.model.ActivityStatus
import kr.co.nuni.gildam.data.model.SyncState
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.sync.TrackSyncMapper.RemoteAction
import kr.co.nuni.gildam.sync.api.TrackDto
import kr.co.nuni.gildam.sync.api.TrackListResponseDto
import kr.co.nuni.gildam.sync.api.ConflictResponseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSyncMapperTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val id = "3f1c2a9e-7b6d-4e0f-9a21-5c8b7d6e4f30"
    private val act = ActivityEntity(id, title = "청계천", transportType = TransportType.BIKE, status = ActivityStatus.COMPLETED,
        startedAtEpochMs = 1_000, endedAtEpochMs = 61_000, totalDurationMs = 60_000, distanceMeters = 500.0, elevationGainMeters = 12f,
        syncState = SyncState.PENDING_UPLOAD, updatedAtEpochMs = 70_000)
    private val pts = listOf(
        TrackPointEntity(activityId = id, seq = 1, latitude = 37.51, longitude = 127.01, altitudeMeters = 31f, speedMps = 1.2f, horizontalAccuracyMeters = 4f, recordedAtEpochMs = 2_000),
        TrackPointEntity(activityId = id, seq = 0, latitude = 37.50, longitude = 127.00, recordedAtEpochMs = 1_000))
    private val wp = WaypointEntity("wp-1234567890", id, 37.5, 127.0, recordedAtEpochMs = 1_500, memoText = "벚꽃", caption = "사진",
        hasPhoto = true, photoId = "ph_x", capturedDeviceName = "Pixel")

    @Test fun toDtoMapsFieldsAndOrdersPoints() {
        val d = TrackSyncMapper.toDto(act, pts, listOf(wp))
        assertEquals(70_000L, d.updatedAt); assertEquals("청계천", d.title)
        assertEquals(60.0, d.durationS!!, 1e-9); assertEquals(500.0, d.distanceM!!, 1e-9)
        assertEquals(listOf(1_000L, 2_000L), d.points.map { it.t })
        assertNull(d.points[0].ele); assertNull(d.points[0].acc); assertEquals(4.0, d.points[1].acc!!, 1e-9)
        assertEquals(true, d.waypoints[0].photoLocalOnly); assertEquals(1, d.waypoints[0].photoCount)
        assertEquals("벚꽃", d.waypoints[0].memo); assertEquals("사진", d.waypoints[0].title)
    }

    @Test fun putBodyHasNoMediaFieldsOrNulls() {
        val s = json.encodeToString(TrackDto.serializer(), TrackSyncMapper.toDto(act, pts, listOf(wp)))
        assertFalse(Regex("uri|url|path|base64|image|photo_id|device", RegexOption.IGNORE_CASE).containsMatchIn(s))
        assertFalse(s.contains("null"))
        assertFalse(s.contains("\"deleted\"")); assertFalse(s.contains("server_seq"))
        assertTrue(s.contains("\"updated_at\":70000"))
    }

    @Test fun invalidWaypointIdsAreDropped() {
        val d = TrackSyncMapper.toDto(act, pts, listOf(wp.copy(id = "short"), wp.copy(id = "has space in id")))
        assertTrue(d.waypoints.isEmpty())
    }

    @Test fun idRegex() {
        assertTrue(TrackSyncMapper.isValidId(id)); assertFalse(TrackSyncMapper.isValidId("abc")); assertFalse(TrackSyncMapper.isValidId("a/b-c-d-e-f"))
    }

    @Test fun parsesListWithTombstone() {
        val body = """{"items":[{"id":"$id","updated_at":5,"title":"t","points":[{"lat":1.0,"lon":2.0,"t":3}],"waypoints":[],"deleted":false,"server_seq":7},
            {"id":"gone-12345678","deleted":true,"updated_at":9,"server_seq":8}],"cursor":8,"has_more":false}"""
        val r = json.decodeFromString(TrackListResponseDto.serializer(), body)
        assertEquals(8L, r.cursor); assertFalse(r.hasMore)
        assertEquals(true, r.items[1].deleted); assertTrue(r.items[1].points.isEmpty())
    }

    @Test fun parsesConflict() {
        val c = json.decodeFromString(ConflictResponseDto.serializer(), """{"error":"conflict","error_description":"x","current":{"id":"$id","updated_at":99,"points":[]}}""")
        assertEquals(99L, c.current!!.updatedAt)
    }

    @Test fun fromDtoKeepsLocalOnlyInfo() {
        val dto = TrackSyncMapper.toDto(act, pts, listOf(wp)).copy(updatedAt = 80_000, title = "새 제목")
        val t = TrackSyncMapper.fromDto(id, dto, act, listOf(wp), "sub-1")
        assertEquals(TransportType.BIKE, t.activity.transportType)
        assertEquals(SyncState.SYNCED, t.activity.syncState); assertEquals(80_000L, t.activity.updatedAtEpochMs)
        assertEquals("새 제목", t.activity.title); assertEquals(2, t.points.size); assertEquals(listOf(0, 1), t.points.map { it.seq })
        assertEquals("ph_x", t.waypoints[0].photoId); assertTrue(t.waypoints[0].hasPhoto)
        val fresh = TrackSyncMapper.fromDto(id, dto, null, emptyList(), "sub-1")
        assertFalse(fresh.waypoints[0].hasPhoto); assertNull(fresh.waypoints[0].photoId)
        assertEquals(TransportType.BIKE, fresh.activity.transportType)
    }

    @Test fun decideRules() {
        val synced = act.copy(syncState = SyncState.SYNCED)
        assertEquals(RemoteAction.APPLY, TrackSyncMapper.decide(null, 10, false))
        assertEquals(RemoteAction.IGNORE, TrackSyncMapper.decide(null, 10, true))
        assertEquals(RemoteAction.APPLY, TrackSyncMapper.decide(synced, 80_000, false))
        assertEquals(RemoteAction.IGNORE, TrackSyncMapper.decide(synced, 70_000, false))
        assertEquals(RemoteAction.SKIP_LOCAL_NEWER, TrackSyncMapper.decide(act, 60_000, false))
        assertEquals(RemoteAction.APPLY, TrackSyncMapper.decide(act, 90_000, false))
        assertEquals(RemoteAction.DELETE_LOCAL, TrackSyncMapper.decide(synced, 80_000, true))
        assertEquals(RemoteAction.SKIP_LOCAL_NEWER, TrackSyncMapper.decide(act, 60_000, true))
        assertEquals(RemoteAction.SKIP_LOCAL_NEWER, TrackSyncMapper.decide(act.copy(status = ActivityStatus.RECORDING, syncState = SyncState.SYNCED), 99_000, false))
    }

    @Test fun activityTypeIsSentAndReadBack() {
        val d = TrackSyncMapper.toDto(act.copy(transportType = TransportType.HIKE), pts, emptyList())
        assertEquals("HIKE", d.activityType)
        assertTrue(json.encodeToString(TrackDto.serializer(), d).contains("\"activity_type\":\"HIKE\""))
        val back = TrackSyncMapper.fromDto(id, d.copy(activityType = "RUN"), act, emptyList(), "sub")
        assertEquals(TransportType.RUN, back.activity.transportType)
    }
    @Test fun missingOrUnknownActivityTypeKeepsLocal() {
        val d = TrackSyncMapper.toDto(act, pts, emptyList())
        assertEquals(TransportType.BIKE, TrackSyncMapper.fromDto(id, d.copy(activityType = null), act, emptyList(), "s").activity.transportType)
        assertEquals(TransportType.BIKE, TrackSyncMapper.fromDto(id, d.copy(activityType = "SKI"), act, emptyList(), "s").activity.transportType)
        assertEquals(TransportType.WALK, TrackSyncMapper.fromDto(id, d.copy(activityType = null), null, emptyList(), "s").activity.transportType)
        assertEquals(TransportType.HIKE, TrackSyncMapper.parseActivityType(" hike "))
    }
}
