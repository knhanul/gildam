package kr.co.nuni.gildam.data.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 계기판 숫자 표기 (순수 함수 → 단위 테스트 가능).
 * 1초마다 값이 바뀌어도 자리수가 흔들리지 않도록 모두 고정폭으로 맞춥니다(화면에서 tabular 적용).
 */
object GaugeFormat {
    private val CLOCK_HM = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREA)
    private val CLOCK_HMS = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.KOREA)

    /** 누적 거리: 미터 → "2.24" (단위 km는 라벨에 표시) */
    fun distanceKm(meters: Double): String =
        String.format(Locale.KOREA, "%.2f", (meters.coerceAtLeast(0.0)) / 1000.0)

    /** 속도: m/s → "5.0" (단위 km/h는 라벨에 표시) */
    fun speedKmh(mps: Float): String =
        String.format(Locale.KOREA, "%.1f", (mps.coerceAtLeast(0f)) * 3.6f)

    /** 경과 시간: "01:23:45" */
    fun durationHms(durationMs: Long): String {
        val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return String.format(Locale.KOREA, "%02d:%02d:%02d", h, m, s)
    }

    /** 현재 시각: "18:20" */
    fun clockHm(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        CLOCK_HM.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    /** 현재 시각(초까지): "18:20:05" */
    fun clockHms(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        CLOCK_HMS.format(Instant.ofEpochMilli(epochMs).atZone(zone))
}
