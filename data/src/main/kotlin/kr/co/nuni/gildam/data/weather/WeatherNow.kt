package kr.co.nuni.gildam.data.weather

/**
 * 길담 API GET /v1/weather/now 응답을 앱에서 쓰는 형태.
 * 필드가 없거나 null 이어도 파서가 여기로 맞춰 줍니다.
 */
data class WeatherNow(
    val source: WeatherSource? = null,
    val observedAtIso: String? = null,
    val tempC: Double? = null,
    val humidity: Double? = null,
    val precipType: PrecipType = PrecipType.NONE,
    val precip1hMm: Double? = null,
    val windSpeedMs: Double? = null,
    val windDirDeg: Double? = null,
    val windGustMs: Double? = null,
    val sky: SkyCondition? = null
) {
    fun hasDisplayableData(): Boolean =
        tempC != null ||
            windSpeedMs != null ||
            windDirDeg != null ||
            windGustMs != null ||
            precipType != PrecipType.NONE ||
            sky != null
}

enum class WeatherSource { KMA, OPEN_METEO }

enum class PrecipType { NONE, RAIN, SNOW, SLEET }

enum class SkyCondition { CLEAR, CLOUDY, OVERCAST }
