package kr.co.nuni.gildam.data.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherNowParserTest {

    @Test
    fun parsesCanonicalCamelCaseJson() {
        val weather = WeatherNowParser.parse(
            """
            {
              "source": "KMA",
              "observedAt": "2026-11-15T02:00:00Z",
              "tempC": 18.4,
              "humidity": 62,
              "precipType": "NONE",
              "precip1hMm": 0,
              "windSpeedMs": 3.2,
              "windDirDeg": 315,
              "windGustMs": null,
              "sky": null
            }
            """.trimIndent()
        )
        assertNotNull(weather)
        weather!!
        assertEquals(WeatherSource.KMA, weather.source)
        assertEquals("2026-11-15T02:00:00Z", weather.observedAtIso)
        assertEquals(18.4, weather.tempC!!, 0.0001)
        assertEquals(62.0, weather.humidity!!, 0.0001)
        assertEquals(PrecipType.NONE, weather.precipType)
        assertEquals(0.0, weather.precip1hMm!!, 0.0001)
        assertEquals(3.2, weather.windSpeedMs!!, 0.0001)
        assertEquals(315.0, weather.windDirDeg!!, 0.0001)
        assertNull(weather.windGustMs)
        assertNull(weather.sky)
        assertTrue(weather.hasDisplayableData())
    }

    @Test
    fun kmaPayloadMayOmitGustAndSkyEntirely() {
        val weather = WeatherNowParser.parse(
            """
            {
              "source": "KMA",
              "observedAt": "2026-11-15T02:00:00Z",
              "tempC": 18.4,
              "humidity": 62,
              "precipType": "NONE",
              "precip1hMm": 0,
              "windSpeedMs": 3.2,
              "windDirDeg": 315
            }
            """.trimIndent()
        )!!
        assertEquals(WeatherSource.KMA, weather.source)
        assertNull(weather.windGustMs)
        assertNull(weather.sky)
        assertEquals(18.4, weather.tempC!!, 0.0001)
        assertEquals(3.2, weather.windSpeedMs!!, 0.0001)
        assertTrue(weather.hasDisplayableData())
    }

    @Test
    fun acceptsSnakeCaseAndIntegerNumbers() {
        val weather = WeatherNowParser.parse(
            """
            {
              "source": "open-meteo",
              "observed_at": "2026-03-01T12:00:00+09:00",
              "temp_c": 7,
              "humidity": "81.5",
              "precip_type": "RAIN",
              "precip_1h_mm": 1,
              "wind_speed_ms": 11,
              "wind_dir_deg": "200",
              "wind_gust_ms": 16,
              "sky": "partly_cloudy"
            }
            """.trimIndent()
        )!!
        assertEquals(WeatherSource.OPEN_METEO, weather.source)
        assertEquals(7.0, weather.tempC!!, 0.0)
        assertEquals(81.5, weather.humidity!!, 0.0001)
        assertEquals(PrecipType.RAIN, weather.precipType)
        assertEquals(1.0, weather.precip1hMm!!, 0.0)
        assertEquals(11.0, weather.windSpeedMs!!, 0.0)
        assertEquals(200.0, weather.windDirDeg!!, 0.0)
        assertEquals(16.0, weather.windGustMs!!, 0.0)
        assertEquals(SkyCondition.CLOUDY, weather.sky)
    }

    @Test
    fun missingAndNullFieldsAreTolerated() {
        val weather = WeatherNowParser.parse(
            """
            {
              "source": null,
              "tempC": 12,
              "humidity": null,
              "precipType": null,
              "windGustMs": null,
              "sky": null,
              "unexpected": true
            }
            """.trimIndent()
        )!!
        assertNull(weather.source)
        assertEquals(12.0, weather.tempC!!, 0.0)
        assertNull(weather.humidity)
        assertEquals(PrecipType.NONE, weather.precipType)
        assertNull(weather.windSpeedMs)
        assertNull(weather.windGustMs)
        assertNull(weather.sky)
        assertTrue(weather.hasDisplayableData())
    }

    @Test
    fun unknownEnumsDoNotDropTheRest() {
        val weather = WeatherNowParser.parse(
            """
            {
              "source": "WEATHERKIT",
              "tempC": -3.5,
              "precipType": "HAIL",
              "sky": "FOG",
              "windSpeedMs": 1.1
            }
            """.trimIndent()
        )!!
        assertNull(weather.source)
        assertEquals(-3.5, weather.tempC!!, 0.0001)
        assertEquals(PrecipType.NONE, weather.precipType)
        assertNull(weather.sky)
        assertEquals(1.1, weather.windSpeedMs!!, 0.0001)
    }

    @Test
    fun emptyObjectIsNotDisplayable() {
        val weather = WeatherNowParser.parse("{}")!!
        assertFalse(weather.hasDisplayableData())
    }

    @Test
    fun invalidJsonReturnsNull() {
        assertNull(WeatherNowParser.parse("not-json"))
        assertNull(WeatherNowParser.parse("[]"))
        assertNull(WeatherNowParser.parse(""))
    }
}
