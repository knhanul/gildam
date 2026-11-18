package kr.co.nuni.gildam.data.elevation

import kr.co.nuni.gildam.data.local.entity.TrackPointEntity
import kotlin.math.abs

/**
 * 오르막·내리막 실시간 누적 및 저장 경로 고도 백필 (순수 Kotlin, Android 의존성 없음 → 단위 테스트 가능).
 *
 * 1) 소스 우선순위: 기압 센서(있으면 기압 고도만) → 없으면 GPS 고도(verticalAccuracy가 나쁜 값은 제외).
 * 2) 기압 → 고도는 기압 센서 값을 표준 대기 해면 기압 기준 상대 고도로 바꾼 값(또는 SensorManager.getAltitude 결과)을 받으며,
 *    기압 고도는 상대 변화만 누적합니다. 해발 표시용 기준 오프셋은 정확도 좋은 GPS 고도로 가끔 부드럽게 보정합니다.
 * 3) 노이즈 처리: 지수 이동 평균(저역 필터)으로 다듬고, 히스테리시스 임계값(기압 2 m, GPS 5 m)을 넘게 바뀌었을 때만
 *    오르막/내리막에 더합니다. 같은 방향으로 이어지는 구간의 꼭대기·바닥(극값)을 기억해 두고,
 *    방향이 바뀌거나 정지·종료([finish])될 때 임계값에 못 미친 나머지도 극값 기준으로 더해 잃지 않습니다.
 *    임계값을 한 번도 넘지 않은 흔들림(노이즈)은 더하지 않습니다.
 * 4) 정지 중(속도 ≈ 0 또는 최근 실제 이동 없음)에는 이미 확정된 구간만 마무리하고, 기준점과 필터를
 *    현재 측정값에 맞춰 기압 표류가 재출발 후 누적되지 않게 합니다.
 */
class ElevationAccumulator(
    val hasBarometer: Boolean,
    initialGainM: Float = 0f,
    initialLossM: Float = 0f,
    private val baroAlpha: Double = DEFAULT_BARO_ALPHA,
    private val gpsAlpha: Double = DEFAULT_GPS_ALPHA,
    private val baroHysteresisM: Double = BARO_HYSTERESIS_M,
    private val gpsHysteresisM: Double = GPS_HYSTERESIS_M,
    private val maxVerticalAccuracyM: Float = MAX_GPS_VERTICAL_ACCURACY_M,
    private val maxStepJumpM: Double = MAX_STEP_JUMP_M
) {
    enum class Source { BAROMETER, GPS }

    val activeSource: Source = if (hasBarometer) Source.BAROMETER else Source.GPS

    var gainM: Float = initialGainM.coerceAtLeast(0f)
        private set

    var lossM: Float = initialLossM.coerceAtLeast(0f)
        private set

    /** 가장 최근 보정된 해발 고도(m). 기압이 있으면 기압 변화 + GPS 오프셋 보정값, 없으면 필터된 GPS 고도 */
    val calibratedSeaLevelM: Float?
        get() = when (activeSource) {
            Source.BAROMETER -> smoothedAlt?.let { (it + seaLevelOffsetM).toFloat() }
            Source.GPS -> smoothedAlt?.toFloat()
        }

    private var smoothedAlt: Double? = null
    /** 마지막으로 오르막·내리막에 반영한 고도 */
    private var referenceAlt: Double? = null
    /** 현재 구간의 극값(오르막이면 꼭대기, 내리막이면 바닥) */
    private var extremumAlt: Double? = null
    /** 현재 구간이 시작된 기준 고도. 극값과의 차이가 임계값 이상이면 확정된 구간으로 봄 */
    private var legOriginAlt: Double? = null
    private var seaLevelOffsetM: Double = 0.0
    private var hasSeaLevelCalibration: Boolean = false
    private var isMoving: Boolean = false
    private var lastSampleResetPending: Boolean = false

    /**
     * 이동 중 여부를 갱신합니다.
     * GPS 위치 업데이트마다 실제 이동 속도/이동 여부로 호출하며, 정지 상태로 바뀌면 누적만 멈추고
     * 기준점이 기압 드리프트를 따라가도록 합니다.
     */
    fun updateMoving(moving: Boolean) {
        if (isMoving && !moving) {
            // 멈추는 순간, 이미 확정된 오르막·내리막의 나머지 높이를 더하고 기준점을 현재 값에 맞춤
            finish()
            smoothedAlt?.let { snapAnchors(it) }
        }
        isMoving = moving
    }

    /**
     * 확정된 구간의 남은 높이를 오르막·내리막에 더합니다.
     * 기록 종료·저장된 경로 계산이 끝날 때 호출해, 마지막 언덕의 임계값 미만 나머지를 잃지 않게 합니다.
     */
    fun finish() {
        commitLeftoverIfConfirmed(hysteresisForSource)
    }

    /** 일시정지·재개 등으로 연속 구간이 끊겼을 때, 남은 높이를 마무리한 뒤 필터/기준점을 새로 잡습니다. */
    fun resetSegment() {
        finish()
        lastSampleResetPending = true
        smoothedAlt = null
        referenceAlt = null
        extremumAlt = null
        legOriginAlt = null
    }

    /**
     * 기압 센서에서 변환한 고도(m, SensorManager.getAltitude 결과) 입력.
     * 기압 센서가 있는 모드에서만 오르막·내리막 누적에 사용됩니다.
     */
    fun onBarometerAltitude(rawAltitudeM: Float, moving: Boolean = isMoving) {
        if (!hasBarometer || !rawAltitudeM.isFinite()) return
        isMoving = moving
        feedSample(
            rawM = rawAltitudeM.toDouble(),
            alpha = baroAlpha,
            hysteresisM = baroHysteresisM,
            moving = moving
        )
    }

    /**
     * GPS 고도(Location.altitude) 입력.
     * - 기압 센서가 있으면 오르막·내리막에는 쓰지 않고, 수직 정확도가 좋을 때 해발 오프셋만 천천히 보정합니다.
     * - 기압 센서가 없으면(GPS 모드) 수직 정확도가 나쁜 값을 거른 뒤 저역 필터 + 히스테리시스로 누적합니다.
     */
    fun onGpsAltitude(
        altitudeM: Double,
        verticalAccuracyM: Float? = null,
        moving: Boolean = isMoving
    ) {
        if (!altitudeM.isFinite() || altitudeM !in MIN_VALID_ALT_M..MAX_VALID_ALT_M) return
        if (verticalAccuracyM != null && (!verticalAccuracyM.isFinite() || verticalAccuracyM <= 0f || verticalAccuracyM > maxVerticalAccuracyM)) {
            return
        }
        isMoving = moving
        if (hasBarometer) {
            calibrateBaroWithGps(altitudeM, verticalAccuracyM)
            return
        }
        feedSample(
            rawM = altitudeM,
            alpha = gpsAlpha,
            hysteresisM = gpsHysteresisM,
            moving = moving
        )
    }

    private fun calibrateBaroWithGps(gpsAltM: Double, verticalAccuracyM: Float?) {
        val baroSmoothed = smoothedAlt ?: return
        if (verticalAccuracyM != null && verticalAccuracyM > BARO_CALIBRATION_MAX_VERT_ACC_M) return
        val targetOffset = gpsAltM - baroSmoothed
        if (!hasSeaLevelCalibration) {
            seaLevelOffsetM = targetOffset
            hasSeaLevelCalibration = true
        } else {
            seaLevelOffsetM += CALIBRATION_ALPHA * (targetOffset - seaLevelOffsetM)
        }
    }

    private val hysteresisForSource: Double
        get() = if (hasBarometer) baroHysteresisM else gpsHysteresisM

    private fun snapAnchors(alt: Double) {
        referenceAlt = alt
        extremumAlt = alt
        legOriginAlt = alt
    }

    private fun commitDelta(from: Double, to: Double) {
        val d = to - from
        when {
            d > 0.0 -> gainM += d.toFloat()
            d < 0.0 -> lossM += (-d).toFloat()
        }
    }

    /** 다듬은 극값이 기준점보다 임계값 이상 벌어졌으면 그 차이를 더하고 기준점을 극값으로 옮김 */
    private fun commitIfPastHysteresis(hysteresisM: Double) {
        val ref = referenceAlt ?: return
        val ext = extremumAlt ?: return
        val d = ext - ref
        if (d >= hysteresisM || d <= -hysteresisM) {
            commitDelta(ref, ext)
            referenceAlt = ext
        }
    }

    /**
     * 이미 임계값을 넘긴 구간이라면, 꼭대기·바닥에 못 미친 나머지도 더하고 기준점을 극값으로 옮김.
     * 임계값을 한 번도 넘지 않은 흔들림은 노이즈로 보고 더하지 않음.
     */
    private fun commitLeftoverIfConfirmed(hysteresisM: Double) {
        val origin = legOriginAlt ?: return
        val ext = extremumAlt ?: return
        val ref = referenceAlt ?: return
        if (abs(ext - origin) < hysteresisM) return
        if (ext != ref) {
            commitDelta(ref, ext)
            referenceAlt = ext
        }
    }

    private fun feedSample(rawM: Double, alpha: Double, hysteresisM: Double, moving: Boolean) {
        if (lastSampleResetPending) {
            lastSampleResetPending = false
            smoothedAlt = rawM
            snapAnchors(rawM)
            return
        }
        val prevSmoothed = smoothedAlt
        if (prevSmoothed == null) {
            smoothedAlt = rawM
            snapAnchors(rawM)
            return
        }
        // 비정상적인 단일 스파이크(센서 글리치·터널 직후 GPS 점프)는 기준을 재설정하고 누적하지 않음
        if (abs(rawM - prevSmoothed) > maxStepJumpM) {
            smoothedAlt = rawM
            snapAnchors(rawM)
            return
        }
        val nextSmoothed = prevSmoothed + alpha * (rawM - prevSmoothed)

        if (!moving) {
            // 확정된 구간만 마무리한 뒤, 필터와 기준점을 현재 측정값에 맞춤 (날씨 드리프트가 재출발 후 더해지지 않게)
            commitLeftoverIfConfirmed(hysteresisM)
            smoothedAlt = rawM
            snapAnchors(rawM)
            return
        }

        smoothedAlt = nextSmoothed
        val ext = extremumAlt ?: nextSmoothed
        val origin = legOriginAlt ?: referenceAlt ?: nextSmoothed
        if (referenceAlt == null) {
            snapAnchors(nextSmoothed)
            return
        }
        if (extremumAlt == null) extremumAlt = ext
        if (legOriginAlt == null) legOriginAlt = origin

        val legDir = when {
            ext > origin -> 1
            ext < origin -> -1
            else -> 0
        }
        if (legDir == 0 || (nextSmoothed - ext) * legDir >= 0) {
            // 같은 방향으로 극값을 갱신. 임계값을 넘을 때마다 더해 실시간 계기판이 따라가게 함
            extremumAlt = nextSmoothed
            commitIfPastHysteresis(hysteresisM)
        } else if (abs(nextSmoothed - ext) >= hysteresisM) {
            // 방향이 바뀜: 직전 꼭대기·바닥까지의 나머지를 더한 뒤, 새 구간을 극값에서 시작
            commitLeftoverIfConfirmed(hysteresisM)
            legOriginAlt = referenceAlt
            extremumAlt = nextSmoothed
            commitIfPastHysteresis(hysteresisM)
        }
        // 극값에서 임계값 미만으로만 되돌아간 흔들림은 극값을 유지 (노이즈)
    }

    companion object {
        const val BARO_HYSTERESIS_M = 2.0
        const val GPS_HYSTERESIS_M = 5.0
        const val DEFAULT_BARO_ALPHA = 0.25
        const val DEFAULT_GPS_ALPHA = 0.35
        const val MAX_GPS_VERTICAL_ACCURACY_M = 25f
        const val BARO_CALIBRATION_MAX_VERT_ACC_M = 15f
        const val MIN_MOVING_SPEED_MPS = 0.4f
        private const val CALIBRATION_ALPHA = 0.1
        private const val MAX_STEP_JUMP_M = 60.0
        private const val MIN_VALID_ALT_M = -500.0
        private const val MAX_VALID_ALT_M = 9_500.0

        /** 표준 해면 기압 기준 기압(hPa) → 고도(m) 변환식 (SensorManager.getAltitude 와 동일 국제 표준 대기 공식) */
        const val SEA_LEVEL_PRESSURE_HPA = 1013.25f

        fun pressureToAltitudeM(pressureHpa: Float, seaLevelHpa: Float = SEA_LEVEL_PRESSURE_HPA): Float {
            if (pressureHpa <= 0f || seaLevelHpa <= 0f) return Float.NaN
            return (44330.0 * (1.0 - Math.pow((pressureHpa / seaLevelHpa).toDouble(), 1.0 / 5.255))).toFloat()
        }

        /**
         * 저장된 경로 점의 고도열로 오르막·내리막(m)을 한 번 계산합니다.
         * 기존 스키마(elevationLossMeters == null) 기록의 백필·상세 표시에 쓰며,
         * 고도 있는 점이 2개 미만이거나 유효 비율이 낮으면 null 을 돌려 '–' 표시로 넘깁니다.
         */
        fun computeFromTrackPoints(points: List<TrackPointEntity>): Pair<Float, Float>? {
            if (points.size < 2) return null
            val withAlt = points.count { it.altitudeMeters != null }
            if (withAlt < 2 || withAlt.toDouble() / points.size < 0.5) return null
            val acc = ElevationAccumulator(hasBarometer = false)
            for (i in points.indices) {
                val pt = points[i]
                if (pt.isPauseBoundary) {
                    acc.resetSegment()
                }
                val alt = pt.altitudeMeters?.toDouble() ?: continue
                // 저장된 경로 점은 이동 필터를 통과해 기록된 점이므로 첫 기준 설정 뒤 이동 중으로 취급하되,
                // speedMps 가 명시적으로 0에 가까운 정지 점만 제외합니다.
                val moving = i == 0 || pt.speedMps == null || pt.speedMps >= MIN_MOVING_SPEED_MPS
                acc.onGpsAltitude(alt, verticalAccuracyM = null, moving = moving)
            }
            acc.finish()
            return acc.gainM to acc.lossM
        }
    }
}
