package kr.co.nuni.gildam.recording

import kr.co.nuni.gildam.data.weather.WeatherNow
import kr.co.nuni.gildam.data.weather.WeatherSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWeatherStateTest {

    @Test
    fun pollIntervalIsThirtyMinutesAndBackoffCapsAtPeriod() {
        assertEquals(30 * 60 * 1000L, LiveWeatherPollScheduler.nextDelayAfterSuccessMs())
        val d1 = LiveWeatherPollScheduler.nextDelayAfterFailureMs(1)
        val d2 = LiveWeatherPollScheduler.nextDelayAfterFailureMs(2)
        val d10 = LiveWeatherPollScheduler.nextDelayAfterFailureMs(10)
        assertTrue(d1 < d2)
        assertEquals(30 * 60 * 1000L, d10)
    }

    @Test
    fun staleAfterSixtyMinutes() {
        val sample = WeatherNow(source = WeatherSource.KMA, tempC = 17.5, windSpeedMs = 3.2)
        val state = LiveWeatherState(weather = sample, fetchedAtEpochMs = 1_000_000L)
        assertFalse(state.isStale(1_000_000L + 59 * 60 * 1000L))
        assertTrue(state.isStale(1_000_000L + 61 * 60 * 1000L))
        assertFalse(LiveWeatherState().isStale(1_000_000L + 120 * 60 * 1000L))
    }
}
