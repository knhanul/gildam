package kr.co.nuni.gildam.ui

import kr.co.nuni.gildam.ui.l10n.*
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
import kr.co.nuni.gildam.data.list.ActivityPlaceInfo
import kr.co.nuni.gildam.data.list.RecordListFilter
import kr.co.nuni.gildam.data.local.entity.ActivityDetailBundle
import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.local.entity.TrackPointEntity
import kr.co.nuni.gildam.data.media.LocalPhotoManager
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.data.prefs.RecordingPrefs
import kr.co.nuni.gildam.data.photo.DevicePhotoFinder
import kr.co.nuni.gildam.data.photo.MatchedPhoto
import kr.co.nuni.gildam.data.prefs.ProfilePrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kr.co.nuni.gildam.data.model.Visibility
import kr.co.nuni.gildam.data.repository.GildamRepository
import kr.co.nuni.gildam.recording.LastKnownLocationProvider
import kr.co.nuni.gildam.recording.LatLonPoint
import kr.co.nuni.gildam.recording.LiveRecordingState
import kr.co.nuni.gildam.recording.RecordingController
import kr.co.nuni.gildam.sync.weather.WeatherRepository
import kr.co.nuni.gildam.sync.worker.GildamSyncWorker
import kr.co.nuni.gildam.ui.screens.StartWeatherUi
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
    private val profilePrefs: ProfilePrefs,
    private val devicePhotoFinder: DevicePhotoFinder,
    private val weatherRepository: WeatherRepository,
    private val lastKnownLocationProvider: LastKnownLocationProvider
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

    // ---- 기록 지역 · 출발/도착 지명 ----

    /** activity_id → 지역 이름 (null 이면 '지역 정보 없음') */
    val regions: StateFlow<Map<String, String?>> = repository.observeActivityRegions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** activity_id → 지역·출발/도착 지명 정보 */
    val places: StateFlow<Map<String, ActivityPlaceInfo>> = repository.observeActivityPlaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val filteredActivities: StateFlow<List<ActivityEntity>> = combine(
        repository.observeAllActivities(),
        places,
        _selectedTransportFilter,
        _searchQuery
    ) { list, placeMap, filter, query ->
        RecordListFilter.filter(list, placeMap, filter, query, hideShort = false).visible
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var regionResolveJob: kotlinx.coroutines.Job? = null

    /**
     * 목록 화면이 뜰 때 지역 이름과 출발·도착 지명을 뒤에서 채웁니다(UI 를 막지 않음).
     * 한 번에 8건씩, 채울 기록이 남아 있으면 이어서 조용히 돕니다.
     */
    fun ensureRegionsResolved() {
        if (regionResolveJob?.isActive == true) return
        regionResolveJob = viewModelScope.launch {
            while (true) {
                val backfilled = runCatching { repository.backfillMissingElevationLoss(limit = 10) }.getOrDefault(0)
                if (backfilled < 10) break
            }
            while (true) {
                val filled = try {
                    repository.resolveMissingRegions(limit = 8)
                } catch (t: Throwable) {
                    android.util.Log.w("GildamViewModel", "region fill failed")
                    0
                }
                if (filled < 8) break
            }
        }
    }

    private val _startWeather = MutableStateFlow<StartWeatherUi>(StartWeatherUi.Hidden)
    val startWeather: StateFlow<StartWeatherUi> = _startWeather.asStateFlow()

    private var weatherAttachedActivityId: String? = null

    init {
        refreshThumbnailCacheSize()
        seedSampleDataIfEmpty()
        checkUnfinishedRecording()
        attachWeatherWhenRecordingStarts()
    }

    /**
     * 출발 화면이 보일 때 마지막 위치로 날씨를 가져옵니다.
     * 비로그인은 '로그인하면 날씨를 볼 수 있어요', 위치 없음·오류는 '날씨 정보 없음'을 보여 줍니다.
     * 날씨 API는 길담 로그인이 필요합니다.
     */
    fun refreshStartWeather() {
        viewModelScope.launch {
            if (liveRecordingState.value.isRecording) return@launch
            if (!authSessionState.value.isLoggedIn) {
                _startWeather.value = StartWeatherUi.NeedsLogin
                return@launch
            }
            val loc = resolveWeatherLocation()
            if (loc == null) {
                _startWeather.value = StartWeatherUi.Unavailable
                return@launch
            }
            weatherRepository.cached(loc.latitude, loc.longitude)?.let {
                _startWeather.value = StartWeatherUi.Ready(it)
                return@launch
            }
            val fetched = weatherRepository.fetchNow(loc.latitude, loc.longitude)
            _startWeather.value = if (fetched != null) {
                StartWeatherUi.Ready(fetched)
            } else {
                StartWeatherUi.Unavailable
            }
        }
    }

    private fun attachWeatherWhenRecordingStarts() {
        viewModelScope.launch {
            liveRecordingState.collect { state ->
                val id = state.activeActivityId ?: return@collect
                if (!state.isRecording) return@collect
                if (weatherAttachedActivityId == id) return@collect
                weatherAttachedActivityId = id
                attachStartWeatherSnapshot(id, state.currentLocation)
            }
        }
    }

    private suspend fun attachStartWeatherSnapshot(activityId: String, current: LatLonPoint?) {
        if (repository.getWeatherSnapshot(activityId) != null) return
        val weather = weatherRepository.lastCached() ?: run {
            val loc = current ?: resolveWeatherLocation() ?: return
            weatherRepository.fetchNow(loc.latitude, loc.longitude)
        } ?: return
        repository.saveWeatherSnapshotIfAbsent(activityId, weather)
    }

    private suspend fun resolveWeatherLocation(): LatLonPoint? {
        val live = liveRecordingState.value
        return live.currentLocation
            ?: live.lastFinishedLocation
            ?: lastKnownLocationProvider.lastLocation()
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

    /**
     * '새 기록' 이동 수단 선택. 화면(칩)과 시작 버튼이 저장소 반영을 기다리지 않도록
     * 메모리에도 즉시 반영하고(다음 기록 시작에 바로 사용), 저장은 뒤에서 합니다.
     */
    private var pendingTransportForNew: TransportType? = null

    fun selectTransportForNewRecording(type: TransportType) {
        pendingTransportForNew = type
        viewModelScope.launch { recordingPrefs.setLastTransport(type.name) }
    }

    fun startRecording() {
        // 방금 고른 활동(코스 따라 걷기 시트 등)이 있으면 그것으로, 없으면 화면에 보이는 이동 수단으로
        val transport = pendingTransportForNew ?: selectedTransportForNewRecording.value
        pendingTransportForNew = null
        val nowStr = LocalDateTime.now().format(shortDateTimeFormatter(appContext.currentLocale()))
        val defaultTitle = appContext.getString(R.string.default_record_title, nowStr, appContext.getString(transport.labelRes()))
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

    // ---- 기록 상세: 같은 날 같은 자리에서 찍은 기기 사진 (사진은 기기에만, URI 만 참조) ----
    /** 사진 찾기 상태: 권한 없음 / 찾는 중 / 결과 */
    data class RecordPhotosState(
        val loading: Boolean = false,
        val permissionDenied: Boolean = false,
        val photos: List<MatchedPhoto> = emptyList(),
        val truncated: Boolean = false,
        val searched: Boolean = false
    )

    private val _recordPhotos = MutableStateFlow(RecordPhotosState())
    val recordPhotos: StateFlow<RecordPhotosState> = _recordPhotos.asStateFlow()
    /** 지금 사진을 찾은 기록 id (다른 기록을 열면 결과를 비움) */
    private var recordPhotosActivityId: String? = null
    /** 진행 중인 찾기 작업 (같은 기록은 중복 실행 안 함) */
    private var recordPhotosJob: kotlinx.coroutines.Job? = null

    /** 기록 상세를 열 때/새로고침할 때 사진 후보를 찾습니다 (IO, 권한 없으면 안내용 상태만) */
    fun findRecordPhotos(activity: ActivityEntity, points: List<TrackPointEntity>) {
        // 다른 기록으로 바뀌면 이전 결과는 바로 지움 (엉뚱한 사진이 잠깐 보이지 않게)
        if (recordPhotosActivityId != activity.id) {
            recordPhotosJob?.cancel()
            recordPhotosActivityId = activity.id
            _recordPhotos.value = RecordPhotosState()
        }
        if (recordPhotosJob?.isActive == true) return
        val endMs = activity.endedAtEpochMs ?: (points.lastOrNull()?.recordedAtEpochMs ?: activity.startedAtEpochMs)
        val route = points.map { Triple(it.latitude, it.longitude, it.recordedAtEpochMs) }
        recordPhotosJob = viewModelScope.launch {
            _recordPhotos.value = RecordPhotosState(loading = true)
            val result = devicePhotoFinder.findForRecord(activity.startedAtEpochMs, endMs, route)
            _recordPhotos.value = RecordPhotosState(
                loading = false,
                permissionDenied = result.permissionDenied,
                photos = result.photos,
                truncated = result.truncated,
                searched = true
            )
        }
    }

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

    /** 여러 기록 일괄 삭제 (짧은 기록 모아서 지우기 등) */
    fun deleteActivities(activityIds: List<String>) {
        if (activityIds.isEmpty()) return
        viewModelScope.launch {
            if (repository.deleteActivities(activityIds)) GildamSyncWorker.enqueueImmediateSync(appContext)
        }
    }

    /** 여러 길기록을 하나로 합치기. 합치면 새 기록 id, 실패하면 null (UI 는 결과만 받아 안내) */
    suspend fun mergeActivities(ids: List<String>, title: String): String? {
        val newId = repository.mergeActivities(ids, title)
        if (newId != null) GildamSyncWorker.enqueueImmediateSync(appContext)
        return newId
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
        val points = repository.getTrackPoints(activityId)
        val waypoints = repository.getWaypoints(activityId)
        val all = repository.getActivityById(activityId) ?: return null
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
