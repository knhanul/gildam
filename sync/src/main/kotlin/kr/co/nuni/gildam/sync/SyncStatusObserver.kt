package kr.co.nuni.gildam.sync

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kr.co.nuni.gildam.sync.worker.GildamSyncWorker
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 설정 화면 계정 카드에 보여줄 동기화 진행 상태.
 * 실제 WorkManager 작업 상태만 반영하고, 결과 여부는 [SyncCursorStore.lastSuccessAtMs]로 확인합니다.
 */
enum class SyncUiStatus {
    /** 예약된 동기화 작업 없음 */
    IDLE,
    /** 동기화 작업 실행 중 */
    SYNCING,
    /** 네트워크 대기 또는 재시도 대기(ENQUEUED/BLOCKED) */
    WAITING,
    /** 마지막 작업이 성공으로 끝남 */
    SUCCEEDED,
    /** 마지막 작업이 실패/취소로 끝남 */
    FAILED
}

@Singleton
class SyncStatusObserver @Inject constructor(@ApplicationContext context: Context) {
    val status: Flow<SyncUiStatus> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(GildamSyncWorker.UNIQUE_WORK_NAME)
            .map { infos ->
                val active = infos.firstOrNull { !it.state.isFinished }
                when {
                    active?.state == WorkInfo.State.RUNNING -> SyncUiStatus.SYNCING
                    active != null -> SyncUiStatus.WAITING
                    infos.isEmpty() -> SyncUiStatus.IDLE
                    infos.last().state == WorkInfo.State.SUCCEEDED -> SyncUiStatus.SUCCEEDED
                    else -> SyncUiStatus.FAILED
                }
            }
}
