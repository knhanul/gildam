package kr.co.nuni.gildam.data.course

import kr.co.nuni.gildam.data.gpxcompare.RuggednessResult
import kr.co.nuni.gildam.data.model.TransportType

/**
 * 코스만 보고 '따라 걷기' 기본 활동을 고르는 규칙 (간단·설명 가능하게).
 *
 * 1) 자전거: 길고(10km 이상) 경사가 완만한 코스
 *    - 1km당 누적 오르막 15m 미만, 평균 경사 3% 미만
 * 2) 등산: 오르막이 뚜렷한 코스
 *    - 누적 오르막 300m 이상 또는 최대 오르막 경사 15% 이상
 * 3) 그 밖에는 걷기
 *
 * 고도 정보가 없으면 자전거/등산 판단 없이 걷기.
 * (사용자가 지난번에 고른 활동이 저장돼 있으면 그 값을 먼저 씁니다.)
 */
object FollowActivityGuess {
    const val BIKE_MIN_DISTANCE_M = 10_000.0
    const val BIKE_MAX_ASCENT_PER_KM_M = 15.0
    const val BIKE_MAX_AVG_GRADE_PCT = 3.0
    const val HIKE_MIN_ASCENT_M = 300.0
    const val HIKE_MIN_MAX_UPHILL_PCT = 15.0

    fun guess(result: RuggednessResult): TransportType {
        if (!result.hasElevation) return TransportType.WALK
        val perKm = if (result.distanceM > 0) result.ascentM / (result.distanceM / 1000.0) else Double.MAX_VALUE
        val longAndFlat = result.distanceM >= BIKE_MIN_DISTANCE_M &&
            perKm < BIKE_MAX_ASCENT_PER_KM_M &&
            result.avgAbsGradePct < BIKE_MAX_AVG_GRADE_PCT
        if (longAndFlat) return TransportType.BIKE
        val mountainous = result.ascentM >= HIKE_MIN_ASCENT_M || result.maxUphillGradePct >= HIKE_MIN_MAX_UPHILL_PCT
        if (mountainous) return TransportType.HIKE
        return TransportType.WALK
    }
}
