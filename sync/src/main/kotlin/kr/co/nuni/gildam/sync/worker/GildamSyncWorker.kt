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
import kr.co.nuni.gildam.auth.KeystoreTokenStore
import kr.co.nuni.gildam.data.local.dao.GildamDao
import kr.co.nuni.gildam.data.model.SyncState
import kr.co.nuni.gildam.sync.api.GildamOvhApi
import kr.co.nuni.gildam.sync.api.TrackPointDto
import kr.co.nuni.gildam.sync.api.TrackPointsChunkRequestDto
import kr.co.nuni.gildam.sync.api.TrackUpsertRequestDto
import kr.co.nuni.gildam.sync.api.WaypointBatchUpsertRequestDto
import kr.co.nuni.gildam.sync.api.WaypointPhotoMetaDto
import kr.co.nuni.gildam.sync.api.WaypointSyncDto
import java.util.concurrent.TimeUnit

/**
 * WorkManager 기반 백그라운드 동기화 워커.
 * - 네트워크 연결 시 대기 중인 Activity, TrackPoints(1,000개 단위 청크), Waypoint 메타데이터를 업로드합니다.
 * - PhotoEntity(MediaStore URI 및 로컬 썸네일)는 절대 읽거나 전송하지 않습니다.
 */
@HiltWorker
class GildamSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: GildamDao,
    private val tokenStore: KeystoreTokenStore,
    private val api: GildamOvhApi
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val accessToken = tokenStore.getAccessToken()
        if (accessToken.isNullOrBlank()) {
            // 비로그인 상태에서는 로컬 전용으로 유지하고 성공 종료
            return Result.success()
        }

        return try {
            val pendingActivities = dao.getActivitiesPendingSync()
            for (activity in pendingActivities) {
                dao.updateActivitySyncState(activity.id, SyncState.SYNCING, activity.pointsSynced)

                // 1단계: Activity 요약 메타데이터 멱등 Upsert
                val metaDto = TrackUpsertRequestDto(
                    trackId = activity.id,
                    title = activity.title,
                    transportType = activity.transportType.name,
                    startedAtEpochMs = activity.startedAtEpochMs,
                    endedAtEpochMs = activity.endedAtEpochMs,
                    totalDurationMs = activity.totalDurationMs,
                    movingDurationMs = activity.movingDurationMs,
                    distanceMeters = activity.distanceMeters,
                    avgSpeedMps = activity.avgSpeedMps,
                    movingAvgSpeedMps = activity.movingAvgSpeedMps,
                    maxSpeedMps = activity.maxSpeedMps,
                    elevationGainMeters = activity.elevationGainMeters,
                    visibility = activity.visibility.name,
                    version = activity.version,
                    clientUpdatedAtEpochMs = activity.updatedAtEpochMs
                )
                val metaResponse = api.upsertTrackMetadata(activity.id, metaDto)
                if (metaResponse.code() == 401) {
                    tokenStore.markReauthRequired()
                    return Result.failure()
                }

                // 2단계: TrackPoints 1,000개 단위 청크 업로드
                if (!activity.pointsSynced) {
                    val allPoints = dao.getTrackPoints(activity.id)
                    allPoints.chunked(1000).forEachIndexed { index, chunk ->
                        val chunkDto = TrackPointsChunkRequestDto(
                            trackId = activity.id,
                            chunkSeq = index,
                            points = chunk.map { pt ->
                                TrackPointDto(
                                    seq = pt.seq,
                                    lat = pt.latitude,
                                    lon = pt.longitude,
                                    ele = pt.altitudeMeters,
                                    speedMps = pt.speedMps,
                                    recordedAtEpochMs = pt.recordedAtEpochMs,
                                    isPauseBoundary = pt.isPauseBoundary
                                )
                            }
                        )
                        api.uploadTrackPointsChunk(activity.id, chunkDto)
                    }
                }

                // 3단계: Waypoints 업로드 (사진 파일 제외, photo_local_only=true 메타만 포함)
                val waypoints = dao.getWaypointsForActivity(activity.id)
                if (waypoints.isNotEmpty()) {
                    val batchDto = WaypointBatchUpsertRequestDto(
                        waypoints = waypoints.map { wpt ->
                            WaypointSyncDto(
                                waypointId = wpt.id,
                                trackId = wpt.activityId,
                                lat = wpt.latitude,
                                lon = wpt.longitude,
                                ele = wpt.altitudeMeters,
                                recordedAtEpochMs = wpt.recordedAtEpochMs,
                                memoText = wpt.memoText,
                                caption = wpt.caption,
                                photoMeta = WaypointPhotoMetaDto(
                                    hasPhoto = wpt.hasPhoto,
                                    photoLocalOnly = true,
                                    photoId = wpt.photoId,
                                    capturedDeviceIdHash = wpt.capturedDeviceIdHash,
                                    capturedDeviceName = wpt.capturedDeviceName
                                ),
                                version = wpt.version,
                                clientUpdatedAtEpochMs = wpt.updatedAtEpochMs
                            )
                        }
                    )
                    api.upsertWaypointsBatch(activity.id, batchDto)
                    waypoints.forEach { wpt ->
                        dao.updateWaypointSyncState(wpt.id, SyncState.SYNCED)
                    }
                }

                dao.updateActivitySyncState(
                    activityId = activity.id,
                    syncState = SyncState.SYNCED,
                    pointsSynced = true
                )
            }
            Result.success()
        } catch (e: Exception) {
            // 오프라인 또는 데모 환경에서 서버 미연결 시 안전하게 지수 백오프 재시도 예약
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "gildam_nuni_cloud_sync"

        fun enqueueImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<GildamSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
