package kr.co.nuni.gildam.recording

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kr.co.nuni.gildam.auth.KeystoreTokenStore
import kr.co.nuni.gildam.data.gpx.GpxExporter
import kr.co.nuni.gildam.data.local.entity.TrackPointEntity
import kr.co.nuni.gildam.data.model.ActivityStatus
import kr.co.nuni.gildam.data.model.TransportType
import kr.co.nuni.gildam.data.repository.GildamRepository
import javax.inject.Inject

/**
 * 화면이 꺼져도 동작하는 location 타입 포그라운드 서비스.
 * - 목표 주기: 약 1초(1,000ms) 간격 수집 (터널/실내/Doze 환경에 따른 흔들림 및 정확도 필터 포함)
 * - Room Write-Ahead Logging 기반 배치 버퍼(5개 포인트 단위) + 일시정지/종료 시 즉시 Flush
 */
@AndroidEntryPoint
class LocationTrackingService : LifecycleService() {

    @Inject lateinit var repository: GildamRepository
    @Inject lateinit var recordingController: RecordingController
    @Inject lateinit var tokenStore: KeystoreTokenStore

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var timerJob: Job? = null

    private var activeActivityId: String? = null
    private var transportType: TransportType = TransportType.WALK
    private var isPaused: Boolean = false
    private var markNextPointAsPauseBoundary: Boolean = false

    private var startTimestampMs: Long = 0L
    private var accumulatedPauseMs: Long = 0L
    private var pauseStartedAtMs: Long = 0L
    private var movingDurationMs: Long = 0L

    private var totalDistanceMeters: Double = 0.0
    private var maxSpeedMps: Float = 0f
    private var elevationGainMeters: Float = 0f
    private var pointSequence: Int = 0
    private var lastValidLocation: Location? = null

    private val pendingRoomBuffer = mutableListOf<TrackPointEntity>()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (location in result.locations) {
                onNewLocationReceived(location)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                val typeName = intent.getStringExtra(EXTRA_TRANSPORT_TYPE) ?: TransportType.WALK.name
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "길 위의 이야기"
                startTracking(TransportType.valueOf(typeName), title)
            }
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startTracking(type: TransportType, title: String) {
        if (activeActivityId != null) return
        transportType = type
        isPaused = false
        startTimestampMs = System.currentTimeMillis()
        accumulatedPauseMs = 0L
        movingDurationMs = 0L
        totalDistanceMeters = 0.0
        maxSpeedMps = 0f
        elevationGainMeters = 0f
        pointSequence = 0
        lastValidLocation = null

        val notification = buildOngoingNotification(distanceKm = 0.0, elapsedSec = 0L, paused = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        lifecycleScope.launch {
            val activity = repository.createNewRecording(
                title = title,
                transportType = type,
                currentUserId = tokenStore.getNuniUserId()
            )
            activeActivityId = activity.id
            recordingController.updateState {
                LiveRecordingState(
                    isRecording = true,
                    isPaused = false,
                    activeActivityId = activity.id,
                    transportType = type
                )
            }
            startTimerLoop()
        }

        // 약 1초 주기 고정밀 위치 요청 (현실적 배터리·스케줄링 허용폭 800ms ~ 2500ms 설정)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(800L)
            .setMaxUpdateDelayMillis(2500L)
            .setWaitForAccurateLocation(false)
            .build()

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun pauseTracking() {
        if (isPaused || activeActivityId == null) return
        isPaused = true
        pauseStartedAtMs = System.currentTimeMillis()
        markNextPointAsPauseBoundary = true

        lifecycleScope.launch {
            flushPointBufferToRoom()
            persistProgress(ActivityStatus.PAUSED)
        }
        recordingController.updateState { it.copy(isPaused = true, currentSpeedMps = 0f) }
        updateNotification()
    }

    private fun resumeTracking() {
        if (!isPaused || activeActivityId == null) return
        isPaused = false
        if (pauseStartedAtMs > 0L) {
            accumulatedPauseMs += (System.currentTimeMillis() - pauseStartedAtMs)
            pauseStartedAtMs = 0L
        }
        recordingController.updateState { it.copy(isPaused = false) }
        updateNotification()
    }

    private fun stopTracking() {
        val finishingId = activeActivityId ?: run {
            stopSelf()
            return
        }
        fusedLocationClient.removeLocationUpdates(locationCallback)
        timerJob?.cancel()

        lifecycleScope.launch {
            flushPointBufferToRoom()
            persistProgress(ActivityStatus.COMPLETED)
            activeActivityId = null
            recordingController.finishState()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun onNewLocationReceived(location: Location) {
        val currentActivityId = activeActivityId ?: return
        if (isPaused) return

        // 수평 정확도가 30m보다 나쁜 경우(터널 진입 직후 등) 신호 미약 배너만 켜고 노이즈 좌표는 스킵
        if (location.hasAccuracy() && location.accuracy > 30f) {
            recordingController.updateState { it.copy(isGpsSignalWeak = true) }
            return
        }

        val prev = lastValidLocation
        var deltaMeters = 0.0
        var computedSpeedMps = if (location.hasSpeed()) location.speed else 0f

        if (prev != null && !markNextPointAsPauseBoundary) {
            deltaMeters = GpxExporter.haversineMeters(
                prev.latitude, prev.longitude,
                location.latitude, location.longitude
            )
            val deltaSec = ((location.time - prev.time).coerceAtLeast(500L)) / 1000.0
            val impliedSpeed = (deltaMeters / deltaSec).toFloat()

            // 비현실적인 GPS 점프(스파이더웹 드리프트) 필터링
            if (impliedSpeed > transportType.maxRealisticSpeedMps) {
                return
            }
            if (!location.hasSpeed()) {
                computedSpeedMps = impliedSpeed
            }

            // 정지 드리프트 방지: 0.8m 이상 실제 이동했을 때만 거리 누적
            if (deltaMeters >= 0.8) {
                totalDistanceMeters += deltaMeters
                movingDurationMs += (location.time - prev.time).coerceIn(0L, 5000L)
            } else {
                computedSpeedMps = 0f
            }

            if (location.hasAltitude() && prev.hasAltitude()) {
                val altDiff = (location.altitude - prev.altitude).toFloat()
                if (altDiff in 0.5f..15.0f) {
                    elevationGainMeters += altDiff
                }
            }
        }

        lastValidLocation = location
        if (computedSpeedMps > maxSpeedMps) {
            maxSpeedMps = computedSpeedMps
        }

        val movingAvgMps = if (movingDurationMs > 1000L) {
            (totalDistanceMeters / (movingDurationMs / 1000.0)).toFloat()
        } else {
            0f
        }

        pointSequence += 1
        val entity = TrackPointEntity(
            activityId = currentActivityId,
            seq = pointSequence,
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeMeters = if (location.hasAltitude()) location.altitude.toFloat() else null,
            speedMps = computedSpeedMps,
            bearing = if (location.hasBearing()) location.bearing else null,
            horizontalAccuracyMeters = location.accuracy,
            recordedAtEpochMs = location.time,
            isPauseBoundary = markNextPointAsPauseBoundary
        )
        markNextPointAsPauseBoundary = false

        synchronized(pendingRoomBuffer) {
            pendingRoomBuffer.add(entity)
        }
        if (pendingRoomBuffer.size >= 5) {
            lifecycleScope.launch {
                flushPointBufferToRoom()
                persistProgress(ActivityStatus.RECORDING)
            }
        }

        val newLatLon = LatLonPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeMeters = entity.altitudeMeters
        )
        recordingController.updateState { state ->
            state.copy(
                distanceMeters = totalDistanceMeters,
                currentSpeedMps = computedSpeedMps,
                movingAvgSpeedMps = movingAvgMps,
                maxSpeedMps = maxSpeedMps,
                elevationGainMeters = elevationGainMeters,
                currentLocation = newLatLon,
                liveTrackPoints = state.liveTrackPoints + newLatLon,
                isGpsSignalWeak = false
            )
        }
    }

    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            while (isActive && activeActivityId != null) {
                if (!isPaused) {
                    val now = System.currentTimeMillis()
                    val activeElapsed = (now - startTimestampMs - accumulatedPauseMs).coerceAtLeast(0L)
                    recordingController.updateState {
                        it.copy(
                            elapsedTotalMs = activeElapsed,
                            elapsedMovingMs = movingDurationMs
                        )
                    }
                    if ((activeElapsed / 1000L) % 5L == 0L) {
                        updateNotification()
                    }
                }
                delay(1000L)
            }
        }
    }

    private suspend fun flushPointBufferToRoom() {
        val batch = synchronized(pendingRoomBuffer) {
            if (pendingRoomBuffer.isEmpty()) return
            val copy = pendingRoomBuffer.toList()
            pendingRoomBuffer.clear()
            copy
        }
        repository.insertTrackPointsBatch(batch)
    }

    private suspend fun persistProgress(status: ActivityStatus) {
        val id = activeActivityId ?: return
        val state = recordingController.liveState.value
        val avgSpeed = if (state.elapsedTotalMs > 1000L) {
            (totalDistanceMeters / (state.elapsedTotalMs / 1000.0)).toFloat()
        } else 0f

        repository.updateRecordingProgress(
            activityId = id,
            status = status,
            totalDurationMs = state.elapsedTotalMs,
            movingDurationMs = movingDurationMs,
            distanceMeters = totalDistanceMeters,
            avgSpeedMps = avgSpeed,
            movingAvgSpeedMps = state.movingAvgSpeedMps,
            maxSpeedMps = maxSpeedMps,
            elevationGainMeters = elevationGainMeters
        )
    }

    private fun updateNotification() {
        val state = recordingController.liveState.value
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            NOTIFICATION_ID,
            buildOngoingNotification(
                distanceKm = state.distanceMeters / 1000.0,
                elapsedSec = state.elapsedTotalMs / 1000L,
                paused = isPaused
            )
        )
    }

    private fun buildOngoingNotification(
        distanceKm: Double,
        elapsedSec: Long,
        paused: Boolean
    ): Notification {
        val hours = elapsedSec / 3600
        val minutes = (elapsedSec % 3600) / 60
        val seconds = elapsedSec % 60
        val timeText = String.format("%02d:%02d:%02d", hours, minutes, seconds)
        val distText = String.format("%.2f km", distanceKm)
        val statusPrefix = if (paused) "일시정지됨" else "길 기록 중"

        val toggleIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = if (paused) ACTION_RESUME else ACTION_PAUSE
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            101,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("길담 · $statusPrefix ($distText)")
            .setContentText("경과 시간 $timeText · 화면이 꺼져도 안전하게 궤적을 기록합니다")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                if (paused) "재개" else "일시정지",
                togglePendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "길담 실시간 여정 기록",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "화면이 꺼져 있을 때도 GPS 궤적을 끊김 없이 기록하기 위한 상시 알림입니다."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "gildam_location_recording_channel"
        const val NOTIFICATION_ID = 4100

        const val ACTION_START = "kr.co.nuni.gildam.recording.ACTION_START"
        const val ACTION_PAUSE = "kr.co.nuni.gildam.recording.ACTION_PAUSE"
        const val ACTION_RESUME = "kr.co.nuni.gildam.recording.ACTION_RESUME"
        const val ACTION_STOP = "kr.co.nuni.gildam.recording.ACTION_STOP"

        const val EXTRA_TRANSPORT_TYPE = "extra_transport_type"
        const val EXTRA_TITLE = "extra_title"
    }
}
