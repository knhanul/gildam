package kr.co.nuni.gildam.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kr.co.nuni.gildam.auth.KeystoreTokenStore
import kr.co.nuni.gildam.auth.NuniAuthManager
import kr.co.nuni.gildam.sync.ServerAccountRepository
import kr.co.nuni.gildam.sync.SyncCursorStore
import kr.co.nuni.gildam.sync.SyncStatusObserver
import kr.co.nuni.gildam.sync.SyncUiStatus
import kr.co.nuni.gildam.auth.NuniSessionState
import kr.co.nuni.gildam.data.gpx.GpxExportOptions
import kr.co.nuni.gildam.data.gpx.GpxExportResult
import kr.co.nuni.gildam.data.gpx.GpxExporter
import kr.co.nuni.gildam.data.local.entity.ActivityDetailBundle
import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.local.entity.TrackPointEntity
import kr.co.nuni.gildam.data.media.LocalPhotoManager
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.data.prefs.RecordingPrefs
import kr.co.nuni.gildam.data.prefs.ProfilePrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kr.co.nuni.gildam.data.model.Visibility
import kr.co.nuni.gildam.data.repository.GildamRepository
import kr.co.nuni.gildam.recording.LiveRecordingState
import kr.co.nuni.gildam.recording.RecordingController
import kr.co.nuni.gildam.sync.worker.GildamSyncWorker
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class GildamViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: GildamRepository,
    private val recordingController: RecordingController,
    private val tokenStore: KeystoreTokenStore,
    private val authManager: NuniAuthManager,
    private val localPhotoManager: LocalPhotoManager,
    private val gpxExporter: GpxExporter,
    private val serverAccount: ServerAccountRepository,
    private val recordingPrefs: RecordingPrefs,
    private val syncStatusObserver: SyncStatusObserver,
    private val syncCursorStore: SyncCursorStore,
    private val profilePrefs: ProfilePrefs
) : ViewModel() {

    /** 로그인 후 GET /v1/me 의 track_count (모르면 null) */
    private val _serverTrackCount = MutableStateFlow<Int?>(null)
    val serverTrackCount: StateFlow<Int?> = _serverTrackCount.asStateFlow()

    fun refreshServerAccount() {
        if (!tokenStore.sessionFlow.value.isLoggedIn) { _serverTrackCount.value = null; return }
        viewModelScope.launch {
            val acc = serverAccount.fetchAccount()
            _serverTrackCount.value = acc?.trackCount
            _serverNickname.value = acc?.nickname
        }
    }

    /** NUNI ID(카카오) 닉네임: 서버 /v1/me 값 */
    private val _serverNickname = MutableStateFlow<String?>(null)

    /** 화면에 보일 닉네임: 이 기기에서 고친 값 > NUNI ID 닉네임 > null('닉네임을 정해 주세요') */
    @OptIn(ExperimentalCoroutinesApi::class)
    val nickname: StateFlow<String?> = tokenStore.sessionFlow
        .flatMapLatest { s -> s.nuniUserId?.let { profilePrefs.nickname(it) } ?: flowOf(null) }
        .combine(_serverNickname) { local, server -> local ?: server }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 1~20자(앞뒤 공백 제거)면 저장하고 true */
    fun updateNickname(raw: String): Boolean {
        val sub = tokenStore.sessionFlow.value.nuniUserId ?: return false
        val nick = ProfilePrefs.normalize(raw) ?: return false
        viewModelScope.launch { profilePrefs.setNickname(sub, nick) }
        return true
    }

    val liveRecordingState: StateFlow<LiveRecordingState> = recordingController.liveState

    val authSessionState: StateFlow<NuniSessionState> = tokenStore.sessionFlow

    /** 동기화 워커의 현재 진행 상태 (실제 WorkManager 상태) */
    val syncStatus: StateFlow<SyncUiStatus> = syncStatusObserver.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncUiStatus.IDLE)

    /** 마지막 동기화 성공 시각(epoch ms). 성공 기록이 없으면 0 */
    val lastSyncSuccessAtMs: StateFlow<Long> = syncCursorStore.lastSuccessAtMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _selectedTransportFilter = MutableStateFlow<TransportType?>(null)
    val selectedTransportFilter: StateFlow<TransportType?> = _selectedTransportFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** 마지막으로 선택한 이동 수단 (DataStore 영속화 → 앱 재시작 시 복원) */
    val selectedTransportForNewRecording: StateFlow<TransportType> = recordingPrefs.lastTransport
        .map { n -> TransportType.entries.firstOrNull { it.name == n } ?: TransportType.WALK }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TransportType.WALK)

    /** 기록 중 화면 켜 두기 설정 (기본값 false) */
    val keepScreenOn: StateFlow<Boolean> = recordingPrefs.keepScreenOn
        .stateIn(viewModelScope, SharingStarted.Eagerly, RecordingPrefs.DEFAULT_KEEP_SCREEN_ON)

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { recordingPrefs.setKeepScreenOn(enabled) }
    }

    /** 자동 종료: 최대 기록 시간(ms, null = 사용 안 함) */
    val maxDurationMs: StateFlow<Long?> = recordingPrefs.maxDurationMs
        .stateIn(viewModelScope, SharingStarted.Eagerly, RecordingPrefs.DEFAULT_MAX_DURATION_MS)

    fun setMaxDurationMs(durationMs: Long?) {
        viewModelScope.launch { recordingPrefs.setMaxDurationMs(durationMs) }
    }

    /** 자동 종료: 오래 멈춤 기준(ms, null = 사용 안 함) */
    val idleLimitMs: StateFlow<Long?> = recordingPrefs.idleLimitMs
        .stateIn(viewModelScope, SharingStarted.Eagerly, RecordingPrefs.DEFAULT_IDLE_LIMIT_MS)

    fun setIdleLimitMs(limitMs: Long?) {
        viewModelScope.launch { recordingPrefs.setIdleLimitMs(limitMs) }
    }

    /** 자동 종료: 확인 대기 시간(ms) */
    val graceMs: StateFlow<Long> = recordingPrefs.graceMs
        .stateIn(viewModelScope, SharingStarted.Eagerly, RecordingPrefs.DEFAULT_GRACE_MS)

    fun setGraceMs(graceMs: Long) {
        viewModelScope.launch { recordingPrefs.setGraceMs(graceMs) }
    }

    private val _thumbnailCacheSizeBytes = MutableStateFlow(0L)
    val thumbnailCacheSizeBytes: StateFlow<Long> = _thumbnailCacheSizeBytes.asStateFlow()

    /** 강제 종료 등으로 완료되지 못한 채 남아 있는 기록 (앱 시작 시 감지) */
    private val _unfinishedRecording = MutableStateFlow<ActivityEntity?>(null)
    val unfinishedRecording: StateFlow<ActivityEntity?> = _unfinishedRecording.asStateFlow()

    val filteredActivities: StateFlow<List<ActivityEntity>> = combine(
        repository.observeAllActivities(),
        _selectedTransportFilter,
        _searchQuery
    ) { list, filter, query ->
        list.filter { item ->
            val matchesTransport = filter == null || item.transportType == filter
            val matchesQuery = query.isBlank() || item.title.contains(query, ignoreCase = true)
            matchesTransport && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshThumbnailCacheSize()
        seedSampleDataIfEmpty()
        checkUnfinishedRecording()
    }

    private fun checkUnfinishedRecording() {
        viewModelScope.launch {
            if (!recordingController.liveState.value.isRecording) {
                _unfinishedRecording.value = repository.getActiveRecording()
            }
        }
    }

    fun resumeUnfinishedRecording() {
        val target = _unfinishedRecording.value ?: return
        _unfinishedRecording.value = null
        recordingController.resumeExistingRecordingService(target.id)
    }

    fun finishUnfinishedRecordingNow() {
        val target = _unfinishedRecording.value ?: return
        _unfinishedRecording.value = null
        viewModelScope.launch {
            if (repository.finishUnfinishedRecording(target.id)) {
                GildamSyncWorker.enqueueImmediateSync(appContext)
            }
        }
    }

    fun selectTransportForNewRecording(type: TransportType) {
        viewModelScope.launch { recordingPrefs.setLastTransport(type.name) }
    }

    fun startRecording() {
        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))
        val transport = selectedTransportForNewRecording.value
        val defaultTitle = "$nowStr ${transport.labelKo} 길이야기"
        recordingController.startRecordingService(transport, defaultTitle)
    }

    fun pauseRecording() = recordingController.pauseRecordingService()

    fun resumeRecording() = recordingController.resumeRecordingService()

    fun stopRecording() {
        recordingController.stopRecordingService()
        GildamSyncWorker.enqueueImmediateSync(appContext)
    }

    fun addWaypointMemo(
        memoText: String,
        caption: String?,
        selectedPhotoUri: Uri?
    ) {
        val live = liveRecordingState.value
        val activeId = live.activeActivityId ?: return
        val lat = live.currentLocation?.latitude ?: 37.5796
        val lon = live.currentLocation?.longitude ?: 126.9770
        val ele = live.currentLocation?.altitudeMeters

        viewModelScope.launch {
            repository.addWaypointWithOptionalPhoto(
                activityId = activeId,
                latitude = lat,
                longitude = lon,
                altitudeMeters = ele,
                memoText = memoText,
                caption = caption,
                mediaStoreUri = selectedPhotoUri,
                isLoggedIn = authSessionState.value.isLoggedIn
            )
            refreshThumbnailCacheSize()
        }
    }

    fun setTransportFilter(type: TransportType?) {
        _selectedTransportFilter.value = type
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun observeActivityDetail(activityId: String): Flow<ActivityDetailBundle?> =
        repository.observeActivityDetail(activityId)

    fun observeTrackPoints(activityId: String): Flow<List<TrackPointEntity>> =
        repository.observeTrackPoints(activityId)

    fun observeActivityDetailWithTrack(activityId: String): Flow<Pair<ActivityDetailBundle?, List<TrackPointEntity>>> =
        combine(
            repository.observeActivityDetail(activityId),
            repository.observeTrackPoints(activityId)
        ) { detail, points -> detail to points }

    fun verifyPhotosOnDetailOpen(bundle: ActivityDetailBundle) {
        viewModelScope.launch {
            repository.verifyPhotosForActivity(bundle)
        }
    }

    fun relinkMissingPhoto(photoId: String, newUri: Uri) {
        viewModelScope.launch {
            repository.relinkMissingPhoto(photoId, newUri)
            refreshThumbnailCacheSize()
        }
    }

    fun removeMissingPhotoKeepMemo(waypointId: String, photoId: String) {
        viewModelScope.launch {
            repository.removeMissingPhotoKeepMemo(waypointId, photoId)
        }
    }

    fun updateTitleAndVisibility(activityId: String, title: String, visibility: Visibility) {
        viewModelScope.launch {
            repository.updateActivityTitleAndVisibility(activityId, title, visibility)
            GildamSyncWorker.enqueueImmediateSync(appContext)
        }
    }

    /** 기록 제목·활동 구분 수정 (로컬 즉시, 이미 올린 기록이면 서버에도) */
    fun editActivity(activityId: String, title: String, type: TransportType) {
        viewModelScope.launch {
            if (repository.updateTitleAndTransport(activityId, title, type)) GildamSyncWorker.enqueueImmediateSync(appContext)
        }
    }

    /** 기록 삭제 (로컬 즉시 숨김, 올린 기록이면 서버 삭제 대기열) */
    fun deleteActivity(activityId: String) {
        viewModelScope.launch {
            if (repository.deleteActivity(activityId)) GildamSyncWorker.enqueueImmediateSync(appContext)
        }
    }

    /** 로그인한 사용자로 이 기록을 클라우드에 올리기 */
    fun uploadActivityToCloud(activityId: String) {
        if (!tokenStore.sessionFlow.value.isLoggedIn) return
        viewModelScope.launch {
            if (repository.requestCloudUpload(activityId)) GildamSyncWorker.enqueueImmediateSync(appContext)
        }
    }

    fun launchKakaoLogin(activityContext: Context, switchAccount: Boolean = false) {
        authManager.launchLogin(activityContext, switchAccount)
    }

    fun triggerDemoLoginAndSync() {
        // 배포 빌드에서는 데모 세션 실행 경로가 없어야 하므로 여기서도 차단합니다.
        if (!BuildConfig.DEBUG) return
        authManager.simulateDemoNuniLogin()
        GildamSyncWorker.enqueueImmediateSync(appContext)
    }

    fun triggerManualSync() {
        GildamSyncWorker.enqueueImmediateSync(appContext)
    }

    fun logout(activityContext: Context) {
        viewModelScope.launch { authManager.logout(activityContext) }
    }

    fun clearThumbnailCache() {
        viewModelScope.launch {
            localPhotoManager.clearLocalThumbnailCache()
            refreshThumbnailCacheSize()
        }
    }

    suspend fun generateSafeGpxExport(
        activityId: String,
        options: GpxExportOptions
    ): GpxExportResult? {
        val detail = repository.observeActivityDetail(activityId)
        val points = repository.getTrackPoints(activityId)
        val waypoints = repository.getWaypoints(activityId)
        val all = filteredActivities.value.find { it.id == activityId } ?: return null
        val xml = gpxExporter.exportToGpxXml(all, points, waypoints, options)
        return GpxExportResult(suggestedFileName = sanitizeGpxFileName(all.title), xml = xml)
    }

    private fun sanitizeGpxFileName(title: String): String {
        val base = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            .ifBlank { "gildam_track" }
        return "$base.gpx"
    }

    fun computeMaskedPreview(points: List<TrackPointEntity>, radiusMeters: Double) =
        gpxExporter.computePrivacyMaskedPoints(points, radiusMeters)

    private fun refreshThumbnailCacheSize() {
        viewModelScope.launch {
            _thumbnailCacheSizeBytes.value = localPhotoManager.getLocalThumbnailCacheSizeBytes()
        }
    }

    /**
     * 안드로이드 스튜디오에서 첫 실행 시 빈 화면이 아닌
     * 설계서의 '북악산 스카이웨이 새벽 걸음 (타 기기 촬영 사진 케이스 & 로컬 메모 포함)'을
     * 바로 확인하고 테스트할 수 있도록 샘플 여정을 1건 자동 생성합니다.
     */
    private fun seedSampleDataIfEmpty() {
        viewModelScope.launch {
            repository.ensureDemoSampleIfEmpty()
        }
    }
}
