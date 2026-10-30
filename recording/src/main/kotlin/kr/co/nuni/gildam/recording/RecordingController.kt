package kr.co.nuni.gildam.recording

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kr.co.nuni.gildam.data.model.TransportType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UI(ViewModel)와 LocationTrackingService 포그라운드 서비스 사이의 반응형 상태 브릿지.
 */
@Singleton
class RecordingController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _liveState = MutableStateFlow(LiveRecordingState())
    val liveState: StateFlow<LiveRecordingState> = _liveState.asStateFlow()

    fun startRecordingService(transportType: TransportType, title: String) {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            putExtra(LocationTrackingService.EXTRA_TRANSPORT_TYPE, transportType.name)
            putExtra(LocationTrackingService.EXTRA_TITLE, title)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun resumeExistingRecordingService(activityId: String) {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_RESUME_EXISTING
            putExtra(LocationTrackingService.EXTRA_ACTIVITY_ID, activityId)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun pauseRecordingService() {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeRecordingService() {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun stopRecordingService(reason: StopReason = StopReason.MANUAL) {
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP
            putExtra(LocationTrackingService.EXTRA_STOP_REASON, reason.name)
        }
        context.startService(intent)
    }

    internal fun updateState(transform: (LiveRecordingState) -> LiveRecordingState) {
        _liveState.update(transform)
    }

    internal fun resetState() {
        _liveState.value = LiveRecordingState()
    }
}
