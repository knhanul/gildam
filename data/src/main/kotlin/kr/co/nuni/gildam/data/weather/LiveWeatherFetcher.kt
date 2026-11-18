package kr.co.nuni.gildam.data.weather

/**
 * 기록 중 실시간 날씨 조회 인터페이스 (:recording 은 :sync 에 직접 의존하지 않으므로 :data 에서 추상화).
 */
interface LiveWeatherFetcher {
    /** 최근 받아 둔 캐시 값과 수신 시각(epoch ms). 없으면 null */
    fun recentCached(nowMs: Long = System.currentTimeMillis()): Pair<WeatherNow, Long>?

    /** 현재 좌표로 날씨를 조회합니다. 백그라운드(IO)에서 호출하며, 실패·미로그인·비정상 응답은 null */
    suspend fun fetchLive(lat: Double, lon: Double): Pair<WeatherNow, Long>?
}
