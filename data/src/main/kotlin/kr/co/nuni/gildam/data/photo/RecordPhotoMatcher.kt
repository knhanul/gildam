package kr.co.nuni.gildam.data.photo

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

/** 사진 후보 (MediaStore + EXIF 에서 읽은 최소 정보). 파일은 복사·전송하지 않고 URI 만 다룹니다. */
data class PhotoCandidate(
    val uri: String,
    val takenAtMs: Long,
    val latitude: Double? = null,
    val longitude: Double? = null
)

/** 기록에 붙는 사진 한 장 */
data class MatchedPhoto(
    val uri: String,
    val takenAtMs: Long,
    /** 기록 시작점부터 이 사진 위치(가장 가까운 경로 점)까지 경로 거리(m). 위치 정보가 없으면 null */
    val alongTrackM: Double?,
    /** 가장 가까운 경로 점까지 거리(m). 위치 정보가 없으면 null */
    val offsetM: Double?,
    /** 위치 정보가 없어 시간만으로 붙였는지 */
    val timeOnly: Boolean,
    /** 지도 핀을 놓을 좌표: 사진 GPS 가 있으면 그 지점, 없으면 가장 가까운 시각의 경로 점 */
    val pointLat: Double? = null,
    val pointLon: Double? = null
)

/**
 * 기록(시간·경로)과 기기 사진을 짝지어 줍니다 (순수 함수).
 *
 * 규칙
 * - 촬영 시각이 기록 시간창 [start - GRACE][end + GRACE] 안이어야 함 (기본 여유 5분)
 * - 사진에 GPS 가 있으면 가장 가까운 경로 점까지 [MAX_DISTANCE_M](200m) 이내일 때만 붙임
 *   (경로 점은 기록 시각 ± [TRACK_SLACK_MS] 안의 점만 비교 — 쉬는 동안 찍은 사진도 붙게)
 * - GPS 가 없으면 시간만으로 붙이고 [MatchedPhoto.timeOnly] = true (화면에 '위치 정보 없음')
 * - 결과는 촬영 시각 순
 */
object RecordPhotoMatcher {
    const val GRACE_MS = 5 * 60 * 1000L
    const val MAX_DISTANCE_M = 200.0
    /** 경로 점 시간 비교 여유 (사진 시각 기준) */
    const val TRACK_SLACK_MS = 15 * 60 * 1000L

    /** 경로 위 한 점 (시각·좌표). 기록 시작점부터 누적 거리 [alongM] 포함 */
    data class RoutePoint(val latitude: Double, val longitude: Double, val recordedAtMs: Long, val alongM: Double)

    /** 기록 시각 순서대로 누적 거리를 붙인 경로 (경로 점이 시각 순이 아니어도 정렬) */
    fun routeWithDistance(
        points: List<Triple<Double, Double, Long>> // lat, lon, recordedAtMs
    ): List<RoutePoint> {
        val sorted = points.sortedBy { it.third }
        var acc = 0.0
        var prev: Triple<Double, Double, Long>? = null
        return sorted.map { p ->
            prev?.let { acc += haversineM(it.first, it.second, p.first, p.second) }
            prev = p
            RoutePoint(p.first, p.second, p.third, acc)
        }
    }

    fun match(
        startMs: Long,
        endMs: Long,
        route: List<RoutePoint>,
        candidates: List<PhotoCandidate>
    ): List<MatchedPhoto> {
        val from = startMs - GRACE_MS
        val to = endMs + GRACE_MS
        val usableRoute = route.filter { it.recordedAtMs in (startMs - TRACK_SLACK_MS)..(endMs + TRACK_SLACK_MS) }
            .ifEmpty { route }
        return candidates.asSequence()
            .filter { it.takenAtMs in from..to }
            .mapNotNull { c ->
                if (c.latitude != null && c.longitude != null && usableRoute.isNotEmpty()) {
                    val nearest = usableRoute.minByOrNull {
                        haversineM(c.latitude, c.longitude, it.latitude, it.longitude)
                    } ?: return@mapNotNull null
                    val d = haversineM(c.latitude, c.longitude, nearest.latitude, nearest.longitude)
                    if (d > MAX_DISTANCE_M) null
                    else MatchedPhoto(
                        c.uri, c.takenAtMs, nearest.alongM, d, timeOnly = false,
                        // 사진 자리에 그대로 찍고, 경로에서 너무 멀지 않으니 지도에는 실제 사진 좌표를 씀
                        pointLat = c.latitude, pointLon = c.longitude
                    )
                } else {
                    // GPS 없음: 시간만으로 붙임 (경로가 있으면 '가장 가까운 시각의 점'을 참고 위치로)
                    val near = usableRoute.minByOrNull { kotlin.math.abs(it.recordedAtMs - c.takenAtMs) }
                    MatchedPhoto(
                        c.uri, c.takenAtMs, near?.alongM, null, timeOnly = true,
                        pointLat = near?.latitude, pointLon = near?.longitude
                    )
                }
            }
            .sortedBy { it.takenAtMs }
            .toList()
    }

    /** 두 좌표 사이 거리(m) */
    fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_008.8
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val h = kotlin.math.sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * kotlin.math.sin(dLon / 2).let { it * it }
        return 2 * r * kotlin.math.asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** 화면 표시용: "12.3 km 지점" / "12.3 km 지점 · 18m 옆" / null */
    fun describePosition(p: MatchedPhoto): String? {
        val along = p.alongTrackM ?: return null
        val km = along / 1000.0
        return if (p.offsetM != null) "%.1f km 지점 · %.0f m 옆".format(km, p.offsetM) else "%.1f km 지점".format(km)
    }

    /** 기록 시간창 (사진 후보 조회용, 여유 포함) */
    fun queryWindow(startMs: Long, endMs: Long): Pair<Long, Long> = (startMs - GRACE_MS) to max(endMs, startMs) + GRACE_MS

    /** 사진 시각 표시 "오후 3:24" */
    fun timeLabel(ms: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String =
        java.time.Instant.ofEpochMilli(ms).atZone(zone)
            .format(java.time.format.DateTimeFormatter.ofPattern("a h:mm", java.util.Locale.KOREAN))
}
