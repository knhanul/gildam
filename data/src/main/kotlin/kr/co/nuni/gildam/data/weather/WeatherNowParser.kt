package kr.co.nuni.gildam.data.weather

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * 날씨 JSON을 [WeatherNow]로 바꿉니다. 필드 이름(camelCase / snake_case),
 * 숫자 타입, 없는 값, 알 수 없는 enum 은 가능한 한 건너뛰고 나머지를 살립니다.
 */
object WeatherNowParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun parse(raw: String): WeatherNow? {
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return null
        return parse(element)
    }

    fun parse(element: JsonElement): WeatherNow? {
        val obj = runCatching { element.jsonObject }.getOrNull() ?: return null
        return WeatherNow(
            source = parseSource(obj.string("source")),
            observedAtIso = obj.string("observedAt", "observed_at"),
            tempC = obj.number("tempC", "temp_c", "temperature"),
            humidity = obj.number("humidity"),
            precipType = parsePrecip(obj.string("precipType", "precip_type")) ?: PrecipType.NONE,
            precip1hMm = obj.number("precip1hMm", "precip_1h_mm", "precip1h"),
            windSpeedMs = obj.number("windSpeedMs", "wind_speed_ms", "windSpeed"),
            windDirDeg = obj.number("windDirDeg", "wind_dir_deg", "windDirection"),
            windGustMs = obj.number("windGustMs", "wind_gust_ms", "windGust"),
            sky = parseSky(obj.string("sky"))
        )
    }

    private fun parseSource(raw: String?): WeatherSource? {
        val key = raw?.trim()?.uppercase()?.replace('-', '_') ?: return null
        return when (key) {
            "KMA" -> WeatherSource.KMA
            "OPEN_METEO", "OPENMETEO" -> WeatherSource.OPEN_METEO
            else -> null
        }
    }

    private fun parsePrecip(raw: String?): PrecipType? {
        val key = raw?.trim()?.uppercase()?.replace('-', '_') ?: return null
        return when (key) {
            "NONE", "NO", "CLEAR", "DRY" -> PrecipType.NONE
            "RAIN", "RAINY" -> PrecipType.RAIN
            "SNOW", "SNOWY" -> PrecipType.SNOW
            "SLEET", "RAIN_SNOW", "RAINSNOW", "WINTRY" -> PrecipType.SLEET
            else -> null
        }
    }

    private fun parseSky(raw: String?): SkyCondition? {
        val key = raw?.trim()?.uppercase()?.replace('-', '_') ?: return null
        return when (key) {
            "CLEAR", "SUNNY", "FAIR" -> SkyCondition.CLEAR
            "CLOUDY", "PARTLY_CLOUDY", "MOSTLY_CLOUDY" -> SkyCondition.CLOUDY
            "OVERCAST", "CLOUDY_ALL" -> SkyCondition.OVERCAST
            else -> null
        }
    }
}

private fun JsonObject.string(vararg keys: String): String? {
    for (key in keys) {
        val el = this[key] ?: continue
        if (el is JsonNull) continue
        val primitive = el as? JsonPrimitive ?: continue
        val value = primitive.contentOrNull?.trim().orEmpty()
        if (value.isNotEmpty() && value != "null") return value
    }
    return null
}

private fun JsonObject.number(vararg keys: String): Double? {
    for (key in keys) {
        val el = this[key] ?: continue
        if (el is JsonNull) continue
        val primitive = el as? JsonPrimitive ?: continue
        primitive.doubleOrNull?.let { return it }
        primitive.intOrNull?.let { return it.toDouble() }
        primitive.longOrNull?.let { return it.toDouble() }
        primitive.contentOrNull?.trim()?.toDoubleOrNull()?.let { return it }
    }
    return null
}
