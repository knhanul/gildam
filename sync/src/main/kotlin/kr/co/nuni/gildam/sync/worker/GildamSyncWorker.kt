package kr.co.nuni.gildam.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import kr.co.nuni.gildam.auth.KeystoreTokenStore
import kr.co.nuni.gildam.data.local.dao.GildamDao
import kr.co.nuni.gildam.data.model.SyncState
import kr.co.nuni.gildam.sync.SyncCursorStore
import kr.co.nuni.gildam.sync.TrackSyncMapper
import kr.co.nuni.gildam.sync.TrackSyncMapper.RemoteAction
import kr.co.nuni.gildam.sync.api.ConflictResponseDto
import kr.co.nuni.gildam.sync.api.GildamOvhApi
import kr.co.nuni.gildam.sync.api.TrackDto
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * gildam-api 동기화 워커.
 * 1) GET /v1/me 로 계정(sub) 확인
 * 2) GET /v1/tracks?since=cursor 를 has_more=false 까지 반복해 서버 변경(삭제 포함)을 로컬에 반영, cursor 저장
 * 3) 로컬 변경분 PUT /v1/tracks/{id} (409 면 서버 최신본으로 덮어씀), 삭제분 DELETE
 * 사진(MediaStore URI·썸네일)은 읽지도 보내지도 않습니다.
 */
@HiltWorker
class GildamSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: GildamDao,
    private val tokenStore: KeystoreTokenStore,
    private val api: GildamOvhApi,
    private val cursorStore: SyncCursorStore,
    private val json: Json
) : CoroutineWorker(appContext, params) {

    private class AuthRequired : Exception()

    override suspend fun doWork(): Result {
        if (tokenStore.getAccessToken().isNullOrBlank()) return Result.success() // 비로그인: 로컬 전용
        return try {
            val me = api.getMe().also { check401(it.code()) }
            if (!me.isSuccessful) return Result.retry()
            val sub = me.body()!!.sub
            pull(sub)
            push(sub)
            Result.success()
        } catch (e: AuthRequired) {
            tokenStore.markReauthRequired()
            Result.failure()
        } catch (e: IOException) {
            Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun check401(code: Int) { if (code == 401) throw AuthRequired() }

    private suspend fun pull(sub: String) {
        var since = cursorStore.get(sub)
        var guard = 0
        while (guard++ < 1000) {
            val res = api.listTracks(since, PAGE_LIMIT)
            check401(res.code())
            if (!res.isSuccessful) throw IOException("list ${res.code()}")
            val page = res.body()!!
            for (item in page.items) {
                val id = item.id ?: continue
                applyRemote(id, item, sub)
            }
            since = page.cursor
            cursorStore.set(sub, since)
            if (!page.hasMore) break
        }
    }

    private suspend fun applyRemote(id: String, item: TrackDto, sub: String) {
        val local = dao.getActivityById(id)
        when (TrackSyncMapper.decide(local, item.updatedAt, item.deleted == true)) {
            RemoteAction.APPLY -> {
                val t = TrackSyncMapper.fromDto(id, item, local, dao.getAllWaypointsForActivity(id), sub)
                dao.replaceActivityFromServer(t.activity, t.points, t.waypoints)
            }
            RemoteAction.DELETE_LOCAL -> dao.hardDeleteActivity(id)
            RemoteAction.SKIP_LOCAL_NEWER, RemoteAction.IGNORE -> Unit
        }
    }

    private suspend fun push(sub: String) {
        for (a in dao.getActivitiesToUpload()) {
            if (!TrackSyncMapper.isValidId(a.id)) { dao.setActivitySyncStateOnly(a.id, SyncState.SYNC_ERROR); continue }
            val body = TrackSyncMapper.toDto(a, dao.getTrackPoints(a.id), dao.getWaypointsForActivity(a.id))
            dao.setActivitySyncStateOnly(a.id, SyncState.SYNCING)
            val res = try { api.putTrack(a.id, body) } catch (e: IOException) {
                dao.setActivitySyncStateOnly(a.id, SyncState.PENDING_UPLOAD); throw e
            }
            if (res.code() == 401) dao.setActivitySyncStateOnly(a.id, SyncState.PENDING_UPLOAD)
            check401(res.code())
            when {
                res.isSuccessful -> {
                    // 올리는 동안 로컬이 또 바뀌었으면 SYNCED 로 못 바꿈 → 다음 동기화에 다시 올림
                    if (dao.markActivitySyncedIfUnchanged(a.id, a.updatedAtEpochMs) == 0) dao.setActivitySyncStateOnly(a.id, SyncState.PENDING_UPLOAD)
                    dao.markWaypointsSyncedForActivity(a.id)
                }
                res.code() == 409 -> {
                    // 서버가 더 최신: 서버 버전으로 로컬을 덮어씀
                    val current = res.errorBody()?.string()?.let { runCatching { json.decodeFromString(ConflictResponseDto.serializer(), it).current }.getOrNull() }
                        ?: api.getTrack(a.id).body()
                    if (current != null) {
                        val t = TrackSyncMapper.fromDto(a.id, current, a, dao.getAllWaypointsForActivity(a.id), sub)
                        dao.replaceActivityFromServer(t.activity, t.points, t.waypoints)
                    }
                }
                res.code() in 400..499 -> dao.setActivitySyncStateOnly(a.id, SyncState.SYNC_ERROR) // 400/404/405/413 등: 재시도해도 안 됨 → '실패' 표시
                else -> { dao.setActivitySyncStateOnly(a.id, SyncState.PENDING_UPLOAD); throw IOException("put ${res.code()}") }
            }
        }
        for (a in dao.getActivitiesToDelete()) {
            val res = api.deleteTrack(a.id, a.deletedAtEpochMs ?: a.updatedAtEpochMs)
            check401(res.code())
            if (res.isSuccessful || res.code() == 404) dao.hardDeleteActivity(a.id)
            else if (res.code() >= 500) throw IOException("delete ${res.code()}")
            // 그 밖의 4xx(405 등): 기록은 숨긴 채 PENDING_DELETE 로 남겨 다음 동기화에 다시 시도
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "gildam_nuni_cloud_sync"
        private const val PAGE_LIMIT = 200

        fun enqueueImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<GildamSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
