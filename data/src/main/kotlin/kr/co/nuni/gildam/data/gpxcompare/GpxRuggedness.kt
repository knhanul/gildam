package kr.co.nuni.gildam.data.gpxcompare

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class RuggednessGrade(val labelKo: String, val descriptionKo: String) {
    EASY("쉬움", "평지 위주, 누구나 걷기 좋은 길"),
    MODERATE("보통", "가벼운 오르내림이 있는 길"),
    SOMEWHAT_HARD("약간 험함", "꾸준한 오르막, 운동화·물 준비"),
    HARD("험함", "가파른 구간이 많아 체력이 필요한 길"),
    VERY_HARD("매우 험함", "긴 급경사, 등산 장비와 경험 권장");
}

/** 경사 구간(절댓값, %) 별 거리 비율. */
data class GradeBucket(val labelKo: String, val fromPct: Double, val toPct: Double?, val distanceM: Double, val ratio: Double)

data class ProfilePoint(val distanceKm: Double, val elevationM: Double)

data class RuggednessResult(
    val displayName: String,
    val pointCount: Int,
    val distanceM: Double,
    val hasElevation: Boolean,
    val ascentM: Double,
    val descentM: Double,
    val maxElevationM: Double?,
    val minElevationM: Double?,
    val maxUphillGradePct: Double,
    val maxDownhillGradePct: Double,
    val avgAbsGradePct: Double,
    val steepDistanceM: Double,
    val steepRatio: Double,
    val gradeBuckets: List<GradeBucket>,
    val profile: List<ProfilePoint>,
    /** 등가거리(km) = 거리 km + 상승 m / 100 */
    val equivalentKm: Double,
    /** 난이도 점수 = 등가거리 + 가파른 구간 km × 2 */
    val score: Double,
    val grade: RuggednessGrade
)

/**
 * 길의 험함 계산 (순수 Kotlin).
 *
 * 1) 위경도 → 하버사인 누적거리
 * 2) 고도를 [STEP_M] 간격으로 다시 뽑고, 앞뒤 [SMOOTH_WINDOW_M] 이동평균으로 GPS 고도 잡음 제거
 * 3) 누적 상승/하강은 [HYSTERESIS_M] 이상 바뀔 때만 셈 (작은 출렁임 무시)
 * 4) 경사는 [GRADE_BIN_M] 구간마다 (고도차 ÷ 거리)로 계산, |경사| ≥ [STEEP_PCT]% 를 '가파른 구간'으로 봄
 */
object GpxRuggedness {
    const val STEP_M = 10.0
    const val SMOOTH_WINDOW_M = 50.0
    const val HYSTERESIS_M = 3.0
    const val GRADE_BIN_M = 50.0
    const val STEEP_PCT = 15.0
    /** 고도 값이 있는 점의 비율이 이보다 낮으면 '고도 없음' */
    const val MIN_ELEVATION_COVERAGE = 0.5
    private const val PROFILE_MAX_POINTS = 160

    private val BUCKETS = listOf(0.0 to 5.0, 5.0 to 10.0, 10.0 to 15.0, 15.0 to 25.0, 25.0 to null)

    fun gradeFor(score: Double): RuggednessGrade = when {
        score < 6 -> RuggednessGrade.EASY
        score < 12 -> RuggednessGrade.MODERATE
        score < 18 -> RuggednessGrade.SOMEWHAT_HARD
        score < 28 -> RuggednessGrade.HARD
        else -> RuggednessGrade.VERY_HARD
    }

    fun haversineM(a: GpxPoint, b: GpxPoint): Double {
        val r = 6_371_008.8
        val dLat = Math.toRadians(b.lat - a.lat); val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * r * asin(min(1.0, sqrt(h)))
    }

    fun analyze(displayName: String, points: List<GpxPoint>): RuggednessResult {
        require(points.size >= 2) { "점이 2개 이상 필요해요" }
        val cum = DoubleArray(points.size)
        for (i in 1 until points.size) cum[i] = cum[i - 1] + haversineM(points[i - 1], points[i])
        val total = cum.last()

        val eleCount = points.count { it.ele != null }
        val hasEle = eleCount >= 2 && eleCount.toDouble() / points.size >= MIN_ELEVATION_COVERAGE && total > 0
        if (!hasEle) {
            val eq = total / 1000.0
            return RuggednessResult(displayName, points.size, total, false, 0.0, 0.0, null, null, 0.0, 0.0, 0.0, 0.0, 0.0,
                emptyList(), emptyList(), eq, eq, gradeFor(eq))
        }

        // 고도 있는 점만으로 거리-고도 쌍 → 등간격 재표본
        val dsKnown = ArrayList<Double>(); val esKnown = ArrayList<Double>()
        points.forEachIndexed { i, p -> p.ele?.let { if (dsKnown.isEmpty() || cum[i] >= dsKnown.last()) { dsKnown += cum[i]; esKnown += it } } }
        val n = max(2, (total / STEP_M).toInt() + 1)
        val step = total / (n - 1)
        val raw = DoubleArray(n) { k -> interp(dsKnown, esKnown, k * step) }
        val half = max(1, (SMOOTH_WINDOW_M / 2 / step).roundToInt())
        val sm = DoubleArray(n) { k ->
            val a = max(0, k - half); val b = min(n - 1, k + half)
            var s = 0.0; for (j in a..b) s += raw[j]; s / (b - a + 1)
        }

        // 히스테리시스 누적 상승/하강
        var ascent = 0.0; var descent = 0.0; var ref = sm[0]
        for (k in 1 until n) {
            val d = sm[k] - ref
            if (d >= HYSTERESIS_M) { ascent += d; ref = sm[k] } else if (d <= -HYSTERESIS_M) { descent -= d; ref = sm[k] }
        }

        // 구간 경사
        val binSteps = max(1, (GRADE_BIN_M / step).roundToInt())
        var maxUp = 0.0; var maxDown = 0.0; var steep = 0.0; var absWeighted = 0.0
        val bucketDist = DoubleArray(BUCKETS.size)
        var k = 0
        while (k < n - 1) {
            val e = min(n - 1, k + binSteps)
            val dist = (e - k) * step
            if (dist > 0) {
                val g = (sm[e] - sm[k]) / dist * 100.0
                if (g > maxUp) maxUp = g
                if (-g > maxDown) maxDown = -g
                val ag = abs(g)
                absWeighted += ag * dist
                if (ag >= STEEP_PCT) steep += dist
                val bi = BUCKETS.indexOfFirst { (lo, hi) -> ag >= lo && (hi == null || ag < hi) }
                bucketDist[if (bi < 0) 0 else bi] += dist
            }
            k = e
        }
        val buckets = BUCKETS.mapIndexed { i, (lo, hi) ->
            val label = if (hi == null) "${lo.toInt()}% 이상" else "${lo.toInt()}–${hi.toInt()}%"
            GradeBucket(label, lo, hi, bucketDist[i], bucketDist[i] / total)
        }

        val stride = max(1, n / PROFILE_MAX_POINTS)
        val profile = (0 until n step stride).map { ProfilePoint(it * step / 1000.0, sm[it]) }
            .let { if ((n - 1) % stride != 0) it + ProfilePoint(total / 1000.0, sm[n - 1]) else it }

        val eq = total / 1000.0 + ascent / 100.0
        val score = eq + steep / 1000.0 * 2.0
        return RuggednessResult(
            displayName, points.size, total, true, ascent, descent, sm.max(), sm.min(),
            maxUp, maxDown, absWeighted / total, steep, steep / total, buckets, profile, eq, score, gradeFor(score)
        )
    }

    private fun interp(xs: List<Double>, ys: List<Double>, x: Double): Double {
        if (x <= xs.first()) return ys.first()
        if (x >= xs.last()) return ys.last()
        var lo = 0; var hi = xs.size - 1
        while (hi - lo > 1) { val m = (lo + hi) / 2; if (xs[m] <= x) lo = m else hi = m }
        val span = xs[hi] - xs[lo]
        return if (span <= 0) ys[lo] else ys[lo] + (ys[hi] - ys[lo]) * (x - xs[lo]) / span
    }
}
