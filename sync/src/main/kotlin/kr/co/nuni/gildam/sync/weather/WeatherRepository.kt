package kr.co.nuni.gildam.sync.weather

import kr.co.nuni.gildam.data.weather.LiveWeatherFetcher
import kr.co.nuni.gildam.data.weather.WeatherNow
import kr.co.nuni.gildam.data.weather.WeatherNowParser
import kr.co.nuni.gildam.sync.api.GildamOvhApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * GET /v1/weather/now (기존 gildam-api 베이스 + 길담 토큰).
 * 401(토큰 없음), 400(위경도 오류), 502(weather_unavailable), 네트워크 오류는 null.
 * source=KMA 이면 windGustMs·sky 는 항상 없고, OPEN_METEO 만 채웁니다.
 * 같은 근처(약 2km) 결과는 15분간 기억해 호출을 줄입니다.
 */
@Singleton
class WeatherRepository @Inject constructor(
    private val api: GildamOvhApi
) : LiveWeatherFetcher {
    private data class Cache(val key: String, val weather: WeatherNow, val atEpochMs: Long)

    @Volatile
    private var cache: Cache? = null

    fun cached(lat: Double, lon: Double, nowMs: Long = System.currentTimeMillis()): WeatherNow? {
        val hit = cache ?: return null
        if (nowMs - hit.atEpochMs > TTL_MS) return null
        if (hit.key != gridKey(lat, lon)) return null
        return hit.weather
    }

    fun lastCached(nowMs: Long = System.currentTimeMillis()): WeatherNow? {
        val hit = cache ?: return null
        if (nowMs - hit.atEpochMs > TTL_MS) return null
        return hit.weather
    }

    override fun recentCached(nowMs: Long): Pair<WeatherNow, Long>? {
        val hit = cache ?: return null
        if (nowMs - hit.atEpochMs > TTL_MS) return null
        return hit.weather to hit.atEpochMs
    }

    override suspend fun fetchLive(lat: Double, lon: Double): Pair<WeatherNow, Long>? {
        val now = System.currentTimeMillis()
        val hit = cache
        if (hit != null && now - hit.atEpochMs <= LIVE_FRESH_MS && hit.key == gridKey(lat, lon)) {
            return hit.weather to hit.atEpochMs
        }
        val fetched = fetchUncached(lat, lon) ?: return null
        val savedAt = cache?.atEpochMs ?: System.currentTimeMillis()
        return fetched to savedAt
    }

    suspend fun fetchNow(lat: Double, lon: Double): WeatherNow? {
        cached(lat, lon)?.let { return it }
        return fetchUncached(lat, lon)
    }

    private suspend fun fetchUncached(lat: Double, lon: Double): WeatherNow? {
        val response = runCatching { api.getWeatherNow(lat, lon) }.getOrNull() ?: return null
        if (!response.isSuccessful) return null
        val body = response.body() ?: return null
        val weather = WeatherNowParser.parse(body)?.takeIf { it.hasDisplayableData() } ?: return null
        cache = Cache(gridKey(lat, lon), weather, System.currentTimeMillis())
        return weather
    }

    private fun gridKey(lat: Double, lon: Double): String =
        "${(lat * 50).roundToInt()}:${(lon * 50).roundToInt()}"

    private companion object {
        const val TTL_MS = 15 * 60 * 1000L
        /** 기록 시작 직후 출발 시트에서 방금 받아 둔(5분 이내) 값은 그대로 재사용 */
        const val LIVE_FRESH_MS = 5 * 60 * 1000L
    }
}
