package kr.co.nuni.gildam.data.gpx

import kr.co.nuni.gildam.data.local.entity.ActivityEntity
import kr.co.nuni.gildam.data.local.entity.TrackPointEntity
import kr.co.nuni.gildam.data.local.entity.WaypointEntity
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * GPX 내보내기 및 공개 공유 화이트리스트 필터 옵션.
 * - 기본값은 가장 안전한 공개 화이트리스트 기준(캡션·메모·절대시각 제외 + 출발/도착 집 근처 200m 마스킹)입니다.
 * - 사진 파일은 어떤 옵션에서도 GPX에 포함되지 않습니다.
 */
data class GpxExportOptions(
    val includeWaypointsAndMemos: Boolean = false,
    val includeCaptions: Boolean = false,
    val includeTimestamps: Boolean = false,
    val privacyMaskRadiusMeters: Double = 200.0
)

data class MaskedPreviewResult(
    val totalPointsCount: Int,
    val visiblePoints: List<TrackPointEntity>,
    val maskedStartCount: Int,
    val maskedEndCount: Int,
    val isOverMaskedWarning: Boolean
)

/** SAF 저장용: 제안 파일명 + GPX XML 본문 */
data class GpxExportResult(
    val suggestedFileName: String,
    val xml: String
)

@Singleton
class GpxExporter @Inject constructor() {

    /**
     * 출발지·도착지 반경(집 근처) 마스킹을 적용한 결과를 미리보기로 계산합니다.
     */
    fun computePrivacyMaskedPoints(
        points: List<TrackPointEntity>,
        radiusMeters: Double
    ): MaskedPreviewResult {
        if (points.isEmpty() || radiusMeters <= 0.0) {
            return MaskedPreviewResult(
                totalPointsCount = points.size,
                visiblePoints = points,
                maskedStartCount = 0,
                maskedEndCount = 0,
                isOverMaskedWarning = false
            )
        }

        val startPt = points.first()
        val endPt = points.last()

        val firstOutsideStartIdx = points.indexOfFirst { pt ->
            haversineMeters(startPt.latitude, startPt.longitude, pt.latitude, pt.longitude) > radiusMeters
        }
        val lastOutsideEndIdx = points.indexOfLast { pt ->
            haversineMeters(endPt.latitude, endPt.longitude, pt.latitude, pt.longitude) > radiusMeters
        }

        if (firstOutsideStartIdx == -1 || lastOutsideEndIdx == -1 || firstOutsideStartIdx >= lastOutsideEndIdx) {
            return MaskedPreviewResult(
                totalPointsCount = points.size,
                visiblePoints = emptyList(),
                maskedStartCount = points.size,
                maskedEndCount = 0,
                isOverMaskedWarning = true
            )
        }

        val visible = points.subList(firstOutsideStartIdx, lastOutsideEndIdx + 1)
        val isOverMasked = visible.size < (points.size * 0.2)

        return MaskedPreviewResult(
            totalPointsCount = points.size,
            visiblePoints = visible,
            maskedStartCount = firstOutsideStartIdx,
            maskedEndCount = points.size - 1 - lastOutsideEndIdx,
            isOverMaskedWarning = isOverMasked
        )
    }

    /**
     * 화이트리스트(허용된 필드만) 기반으로 깨끗한 새 GPX 1.1 XML 문자열을 생성합니다.
     */
    fun exportToGpxXml(
        activity: ActivityEntity,
        points: List<TrackPointEntity>,
        waypoints: List<WaypointEntity>,
        options: GpxExportOptions
    ): String {
        val masked = computePrivacyMaskedPoints(points, options.privacyMaskRadiusMeters)
        val safePoints = masked.visiblePoints

        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<gpx version=\"1.1\" creator=\"Gildam by nuni\" xmlns=\"http://www.topografix.com/GPX/1/1\">")
        sb.appendLine("  <metadata>")
        sb.appendLine("    <name>${escapeXml(activity.title)}</name>")
        if (options.includeTimestamps) {
            sb.appendLine("    <time>${formatIso8601(activity.startedAtEpochMs)}</time>")
        }
        sb.appendLine("  </metadata>")

        // Waypoints (사진 원본/URI는 절대 포함하지 않으며, 사용자가 허용한 경우에만 캡션/메모 포함)
        if (options.includeWaypointsAndMemos) {
            for (wpt in waypoints) {
                sb.appendLine("  <wpt lat=\"${wpt.latitude}\" lon=\"${wpt.longitude}\">")
                wpt.altitudeMeters?.let { sb.appendLine("    <ele>$it</ele>") }
                if (options.includeTimestamps) {
                    sb.appendLine("    <time>${formatIso8601(wpt.recordedAtEpochMs)}</time>")
                }
                if (options.includeCaptions && !wpt.caption.isNullOrBlank()) {
                    sb.appendLine("    <name>${escapeXml(wpt.caption)}</name>")
                }
                if (wpt.memoText.isNotBlank()) {
                    sb.appendLine("    <desc>${escapeXml(wpt.memoText)}</desc>")
                }
                sb.appendLine("  </wpt>")
            }
        }

        sb.appendLine("  <trk>")
        sb.appendLine("    <name>${escapeXml(activity.title)}</name>")
        sb.appendLine("    <type>${activity.transportType.name}</type>")
        sb.appendLine("    <trkseg>")
        for (pt in safePoints) {
            if (pt.isPauseBoundary) {
                sb.appendLine("    </trkseg>")
                sb.appendLine("    <trkseg>")
            }
            sb.appendLine("      <trkpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\">")
            pt.altitudeMeters?.let { sb.appendLine("        <ele>$it</ele>") }
            if (options.includeTimestamps) {
                sb.appendLine("        <time>${formatIso8601(pt.recordedAtEpochMs)}</time>")
            }
            sb.appendLine("      </trkpt>")
        }
        sb.appendLine("    </trkseg>")
        sb.appendLine("  </trk>")
        sb.appendLine("</gpx>")
        return sb.toString()
    }

    private fun formatIso8601(epochMs: Long): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMs))

    private fun escapeXml(input: String): String =
        input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    companion object {
        fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return r * c
        }
    }
}
