package kr.co.nuni.gildam.data.repository

import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.model.SyncState
import kr.co.nuni.gildam.data.model.TransportType

/**
 * 기록 수정·삭제·클라우드 올리기 규칙 (순수 Kotlin, 단위 테스트 대상).
 * - 아직 안 올린 기록(LOCAL_ONLY)은 수정해도 기기에만 남고, 사용자가 '클라우드에 올리기'를 눌러야 올라감
 * - 한 번이라도 올리기를 요청한 기록은 수정하면 PENDING_UPLOAD 로 바뀌어 다음 동기화 때 서버에 반영(오프라인 대기열)
 * - 삭제: 서버에 간 적 없는 기록은 바로 지우고, 그 밖은 PENDING_DELETE 로 숨긴 뒤 워커가 서버 DELETE 후 완전히 지움
 */
object RecordEditPolicy {
    const val MAX_TITLE = 200

    fun cleanTitle(raw: String): String? = raw.trim().replace(Regex("\\s+"), " ").take(MAX_TITLE).ifBlank { null }

    fun applyEdit(a: ActivityEntity, title: String, type: TransportType, now: Long): ActivityEntity? {
        val t = cleanTitle(title) ?: return null
        if (t == a.title && type == a.transportType) return a
        return a.copy(
            title = t,
            transportType = type,
            syncState = if (a.syncState == SyncState.LOCAL_ONLY) SyncState.LOCAL_ONLY else SyncState.PENDING_UPLOAD,
            version = a.version + 1,
            updatedAtEpochMs = maxOf(now, a.updatedAtEpochMs + 1)
        )
    }

    enum class DeleteAction { HARD_DELETE_NOW, QUEUE_SERVER_DELETE }

    fun deleteAction(a: ActivityEntity): DeleteAction =
        if (a.syncState == SyncState.LOCAL_ONLY) DeleteAction.HARD_DELETE_NOW else DeleteAction.QUEUE_SERVER_DELETE

    fun markDeleted(a: ActivityEntity, now: Long): ActivityEntity = a.copy(
        syncState = SyncState.PENDING_DELETE,
        deletedAtEpochMs = now,
        updatedAtEpochMs = maxOf(now, a.updatedAtEpochMs + 1)
    )

    /** 올리기는 끝난 기록(COMPLETED)이고, 아직 안 올렸거나 실패한 경우에만 */
    fun canRequestUpload(a: ActivityEntity): Boolean =
        a.status == kr.co.nuni.gildam.data.model.ActivityStatus.COMPLETED && a.deletedAtEpochMs == null &&
            (a.syncState == SyncState.LOCAL_ONLY || a.syncState == SyncState.SYNC_ERROR)

    fun requestUpload(a: ActivityEntity): ActivityEntity? =
        if (canRequestUpload(a)) a.copy(syncState = SyncState.PENDING_UPLOAD) else null
}
