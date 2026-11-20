package kr.co.nuni.gildam.data.photo

import kotlin.math.abs

/**
 * '길 위의 이야기' 한 곳(웨이포인트)을 눌렀을 때 그곳 사진만 고르기 (순수 함수).
 *
 * 고르는 기준 (하나만 맞아도 포함)
 * - 그 메모에 직접 붙인 사진
 * - 사진 자리(GPS 또는 경로 기준 추정)가 메모 자리에서 [NEAR_M] 안
 * - 메모를 남긴 시각 ± [TIME_WINDOW_MS] 안에 찍은 사진
 */
object WaypointPhotoFilter {
    const val NEAR_M = 50.0
    const val TIME_WINDOW_MS = 3 * 60 * 1000L

    data class Anchor(
        val latitude: Double,
        val longitude: Double,
        val atMs: Long
    )

    /**
     * [photos] 중 [anchor] 근처 사진 + [linked](메모에 직접 붙인 사진, 목록에 없어도 포함). 촬영 시각 순, 같은 사진은 한 번만.
     */
    fun select(anchor: Anchor, photos: List<MatchedPhoto>, linked: List<MatchedPhoto> = emptyList()): List<MatchedPhoto> {
        val linkedKeys = linked.map { mediaKey(it.uri) }.toSet()
        val near = photos.filter { p ->
            mediaKey(p.uri) in linkedKeys || isNear(anchor, p)
        }
        val seen = HashSet<String>()
        // 목록 쪽(경로 위치가 붙은) 사진을 먼저 두고, 목록에 없는 직접 붙인 사진을 더함
        return (near + linked).filter { seen.add(mediaKey(it.uri)) }.sortedBy { it.takenAtMs }
    }

    fun isNear(anchor: Anchor, p: MatchedPhoto): Boolean {
        if (abs(p.takenAtMs - anchor.atMs) <= TIME_WINDOW_MS) return true
        val lat = p.pointLat ?: return false
        val lon = p.pointLon ?: return false
        return RecordPhotoMatcher.haversineM(anchor.latitude, anchor.longitude, lat, lon) <= NEAR_M
    }

    /**
     * 같은 갤러리 사진인지 비교할 열쇠.
     * content://media/external/images/media/123 과 content://media/external_primary/images/media/123 처럼
     * 볼륨 이름만 다른 URI 도 같은 사진으로 봄.
     */
    fun mediaKey(uri: String): String {
        if (!uri.startsWith("content://media/")) return uri
        val id = uri.substringAfterLast('/').substringBefore('?')
        return if (id.isNotEmpty() && id.all { it.isDigit() }) "media:$id" else uri
    }
}
