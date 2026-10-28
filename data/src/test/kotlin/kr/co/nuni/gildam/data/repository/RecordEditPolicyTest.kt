package kr.co.nuni.gildam.data.repository

import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.model.ActivityStatus
import kr.co.nuni.gildam.data.model.SyncState
import kr.co.nuni.gildam.data.model.TransportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordEditPolicyTest {
    private fun a(s: SyncState, status: ActivityStatus = ActivityStatus.COMPLETED) =
        ActivityEntity("act_12345678", title = "산책", status = status, startedAtEpochMs = 1, syncState = s, updatedAtEpochMs = 1000, version = 3)

    @Test fun editLocalOnlyStaysLocal() {
        val e = RecordEditPolicy.applyEdit(a(SyncState.LOCAL_ONLY), "  북한산   등산 ", TransportType.HIKE, 5000)!!
        assertEquals("북한산 등산", e.title); assertEquals(TransportType.HIKE, e.transportType)
        assertEquals(SyncState.LOCAL_ONLY, e.syncState); assertEquals(4, e.version); assertEquals(5000, e.updatedAtEpochMs)
    }
    @Test fun editSyncedQueuesUpload() {
        for (s in listOf(SyncState.SYNCED, SyncState.SYNC_ERROR, SyncState.SYNCING, SyncState.PENDING_UPLOAD))
            assertEquals(SyncState.PENDING_UPLOAD, RecordEditPolicy.applyEdit(a(s), "x", TransportType.RUN, 5000)!!.syncState)
    }
    @Test fun editUpdatedAtAlwaysIncreases() {
        assertEquals(1001, RecordEditPolicy.applyEdit(a(SyncState.SYNCED), "x", TransportType.BIKE, 10)!!.updatedAtEpochMs)
    }
    @Test fun blankTitleRejected() { assertNull(RecordEditPolicy.applyEdit(a(SyncState.SYNCED), "   ", TransportType.WALK, 5)) }
    @Test fun noChangeReturnsSame() { val x = a(SyncState.SYNCED); assertSame(x, RecordEditPolicy.applyEdit(x, "산책", TransportType.WALK, 5)) }
    @Test fun titleCapped() { assertEquals(200, RecordEditPolicy.cleanTitle("가".repeat(300))!!.length) }
    @Test fun deleteRules() {
        assertEquals(RecordEditPolicy.DeleteAction.HARD_DELETE_NOW, RecordEditPolicy.deleteAction(a(SyncState.LOCAL_ONLY)))
        assertEquals(RecordEditPolicy.DeleteAction.QUEUE_SERVER_DELETE, RecordEditPolicy.deleteAction(a(SyncState.SYNCED)))
        assertEquals(RecordEditPolicy.DeleteAction.QUEUE_SERVER_DELETE, RecordEditPolicy.deleteAction(a(SyncState.SYNC_ERROR)))
        val d = RecordEditPolicy.markDeleted(a(SyncState.SYNCED), 9000)
        assertEquals(SyncState.PENDING_DELETE, d.syncState); assertEquals(9000L, d.deletedAtEpochMs)
    }
    @Test fun uploadRules() {
        assertEquals(SyncState.PENDING_UPLOAD, RecordEditPolicy.requestUpload(a(SyncState.LOCAL_ONLY))!!.syncState)
        assertEquals(SyncState.PENDING_UPLOAD, RecordEditPolicy.requestUpload(a(SyncState.SYNC_ERROR))!!.syncState)
        assertNull(RecordEditPolicy.requestUpload(a(SyncState.SYNCED)))
        assertFalse(RecordEditPolicy.canRequestUpload(a(SyncState.LOCAL_ONLY, ActivityStatus.RECORDING)))
        assertTrue(RecordEditPolicy.canRequestUpload(a(SyncState.LOCAL_ONLY)))
    }
}
