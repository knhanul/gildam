package kr.co.nuni.gildam.data.weather

import kotlin.math.abs
import kotlin.math.pow

/** 16방위 바람 이름 (바람이 불어오는 쪽). 화면 글자는 ui/l10n 에서 고릅니다. */
enum class WindCardinal16 { N, NNE, NE, ENE, E, ESE, SE, SSE, S, SSW, SW, WSW, W, WNW, NW, NNW }

fun windCardinal16(dirDeg: Double): WindCardinal16 {
    val n = normalizeDeg(dirDeg)
    return WindCardinal16.entries[((n + 11.25) / 22.5).toInt() % 16]
}

/** 가는 방향에 견준 바람: 맞바람 / 뒷바람 / 옆바람 */
enum class RelativeWind { HEAD, TAIL, CROSS }

/**
 * [windFromDeg] 바람이 불어오는 쪽(기상 관례), [headingDeg] 내가 가는 방향(0=북, 시계 방향).
 * 불어오는 쪽이 가는 방향 ±45° 안이면 맞바람, 반대쪽 ±45° 안이면 뒷바람, 그 밖은 옆바람.
 */
fun relativeWind(windFromDeg: Double, headingDeg: Double): RelativeWind {
    val d = angleDiff(windFromDeg, headingDeg)
    return when {
        d <= 45.0 -> RelativeWind.HEAD
        d >= 135.0 -> RelativeWind.TAIL
        else -> RelativeWind.CROSS
    }
}

/**
 * 화면 위 바람 화살표 회전각(위가 0, 시계 방향). 화살표는 바람이 불어가는 쪽을 가리킴.
 * [mapBearingDeg] 지도가 돌아간 각도 (북쪽 위면 0).
 */
fun windArrowScreenDeg(windFromDeg: Double, mapBearingDeg: Double): Float =
    normalizeDeg(windFromDeg + 180.0 - mapBearingDeg).toFloat()

/**
 * 체감 온도(°C).
 * - 기온 10°C 이하이고 바람 1.3 m/s 이상: 기상청 겨울철 체감온도(풍속 km/h)
 * - 기온 27°C 이상이고 습도 40% 이상: 열지수(Rothfusz)
 * - 그 밖: 기온 그대로
 */
fun feelsLikeC(tempC: Double, windMs: Double?, humidityPct: Double?): Double {
    if (windMs != null && tempC <= 10.0 && windMs >= 1.3) {
        val v = (windMs * 3.6).pow(0.16)
        return 13.12 + 0.6215 * tempC - 11.37 * v + 0.3965 * v * tempC
    }
    if (humidityPct != null && tempC >= 27.0 && humidityPct >= 40.0) {
        val t = tempC * 9.0 / 5.0 + 32.0
        val r = humidityPct
        val hiF = -42.379 + 2.04901523 * t + 10.14333127 * r - 0.22475541 * t * r -
            0.00683783 * t * t - 0.05481717 * r * r + 0.00122874 * t * t * r +
            0.00085282 * t * r * r - 0.00000199 * t * t * r * r
        return maxOf(tempC, (hiF - 32.0) * 5.0 / 9.0)
    }
    return tempC
}

private fun normalizeDeg(d: Double): Double = ((d % 360.0) + 360.0) % 360.0

/** 두 각도 사이 차이 (0..180) */
private fun angleDiff(a: Double, b: Double): Double {
    val d = abs(normalizeDeg(a) - normalizeDeg(b))
    return if (d > 180.0) 360.0 - d else d
}
