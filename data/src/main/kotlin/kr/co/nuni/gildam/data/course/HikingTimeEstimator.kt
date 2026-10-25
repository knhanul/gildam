package kr.co.nuni.gildam.data.course

import kr.co.nuni.gildam.data.gpxcompare.ProfilePoint
import kotlin.math.abs
import kotlin.math.exp

/**
 * 도보 예상 소요 시간 (Tobler 하이킹 함수).
 *
 * 속도 v(km/h) = 6 · exp(-3.5 · |경사 + 0.05|), 경사 = 고도차 ÷ 수평거리.
 * 평지에서 약 5.0 km/h, 약 -5% 내리막에서 최고 6 km/h.
 * 고도 프로파일(이미 50m 이동평균으로 잡음 제거됨) 구간마다 시간을 더합니다.
 * [paceFactor] > 1 이면 느리게(시간 증가), < 1 이면 빠르게.
 */
object HikingTimeEstimator {
    const val FLAT_KMH = 5.036  // 6·e^(-0.175)
    /** 너무 가파른 구간에서 속도가 0에 가까워지는 것 방지 */
    const val MIN_KMH = 0.5
    const val PACE_MIN = 0.6
    const val PACE_MAX = 1.8

    fun toblerKmh(slope: Double): Double = maxOf(MIN_KMH, 6.0 * exp(-3.5 * abs(slope + 0.05)))

    /** 프로파일 [fromKm] 지점부터 끝까지 예상 초. 프로파일이 없으면 평지 속도로 계산. */
    fun estimateSeconds(profile: List<ProfilePoint>, totalDistanceM: Double, paceFactor: Double = 1.0, fromKm: Double = 0.0): Double {
        val pace = paceFactor.coerceIn(PACE_MIN, PACE_MAX)
        if (profile.size < 2) {
            val km = (totalDistanceM / 1000.0 - fromKm).coerceAtLeast(0.0)
            return km / FLAT_KMH * 3600.0 * pace
        }
        var hours = 0.0
        for (i in 1 until profile.size) {
            val a = profile[i - 1]; val b = profile[i]
            val segStart = maxOf(a.distanceKm, fromKm)
            val dxKm = b.distanceKm - segStart
            if (dxKm <= 0) continue
            val fullKm = b.distanceKm - a.distanceKm
            val slope = if (fullKm > 0) (b.elevationM - a.elevationM) / (fullKm * 1000.0) else 0.0
            hours += dxKm / toblerKmh(slope)
        }
        return hours * 3600.0 * pace
    }

    /**
     * 개인 보정 계수 = 실제 이동 시간 ÷ Tobler 예상 시간 (여러 기록의 중앙값).
     * 기록이 없으면 null.
     */
    fun personalPaceFactor(samples: List<Pair<Double, Double>>): Double? {
        val ratios = samples.filter { (actual, est) -> actual > 0 && est > 0 }.map { (a, e) -> a / e }.sorted()
        if (ratios.isEmpty()) return null
        val m = ratios.size / 2
        val median = if (ratios.size % 2 == 1) ratios[m] else (ratios[m - 1] + ratios[m]) / 2
        return median.coerceIn(PACE_MIN, PACE_MAX)
    }

    /** "약 2시간 15분" / "약 40분" */
    fun formatKo(seconds: Double): String {
        val totalMin = Math.round(seconds / 60.0).toInt().let { if (it < 1) 1 else it }
        val rounded = if (totalMin >= 60) ((totalMin + 2) / 5) * 5 else totalMin
        val h = rounded / 60; val m = rounded % 60
        return when { h == 0 -> "${m}분"; m == 0 -> "${h}시간"; else -> "${h}시간 ${m}분" }
    }
}
