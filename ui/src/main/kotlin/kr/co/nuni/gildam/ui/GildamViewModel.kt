package kr.co.nuni.gildam.ui

import android.content.Context
import android.net.Uri
import android.os.PowerManager
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kr.co.nuni.gildam.auth.KeystoreTokenStore
import kr.co.nuni.gildam.auth.NuniAuthManager
import kr.co.nuni.gildam.auth.NuniSessionState
import kr.co.nuni.gildam.data.gpx.GpxExportOptions
import kr.co.nuni.gildam.data.gpx.GpxExporter
import kr.co.nuni.gildam.data.local.entity.ActivityDetailBundle
import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.local.entity.TrackPointEntity
import kr.co.nuni.gildam.data.media.LocalPhotoManager
import kr.co.nuni.gildam.data.model.TransportType
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
    private val gpxExporter: GpxExporter
) : ViewModel() {

    val liveRecordingState: StateFlow<LiveRecordingState> = recordingController.liveState

    val authSessionState: StateFlow<NuniSessionState> = tokenStore.sessionFlow

    private val _selectedTransportFilter = MutableStateFlow<TransportType?>(null)
    val selectedTransportFilter: StateFlow<TransportType?> = _selectedTransportFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTransportForNewRecording = MutableStateFlow(TransportType.WALK)
    val selectedTransportForNewRecording: StateFlow<TransportType> =
        _selectedTransportForNewRecording.asStateFlow()

    private val _thumbnailCacheSizeBytes = MutableStateFlow(0L)
    val thumbnailCacheSizeBytes: StateFlow<Long> = _thumbnailCacheSizeBytes.asStateFlow()

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
    }

    fun selectTransportForNewRecording(type: TransportType) {
        _selectedTransportForNewRecording.value = type
    }

    fun startRecording() {
        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))
        val transport = _selectedTransportForNewRecording.value
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

    fun launchKakaoLogin(activityContext: Context, switchAccount: Boolean = false) {
        authManager.launchLogin(activityContext, switchAccount)
    }

    fun triggerDemoLoginAndSync() {
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

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = appContext.getSystemService(PowerManager::class.java)
        return pm?.isIgnoringBatteryOptimizations(appContext.packageName) ?: true
    }

    suspend fun generateSafeGpxExport(
        activityId: String,
        options: GpxExportOptions
    ): String? {
        val detail = repository.observeActivityDetail(activityId)
        val points = repository.getTrackPoints(activityId)
        val waypoints = repository.getWaypoints(activityId)
        val all = filteredActivities.value.find { it.id == activityId } ?: return null
        return gpxExporter.exportToGpxXml(all, points, waypoints, options)
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
