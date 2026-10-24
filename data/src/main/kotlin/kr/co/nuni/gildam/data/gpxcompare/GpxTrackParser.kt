package kr.co.nuni.gildam.data.gpxcompare

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import javax.xml.parsers.SAXParserFactory

/** 비교용 최소 좌표. 시간·이름·메모 등 개인 정보는 읽지 않습니다. */
data class GpxPoint(val lat: Double, val lon: Double, val ele: Double?)

class GpxParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * GPX 1.0/1.1 에서 trkpt(없으면 rtept)의 위도·경도·고도만 읽는 순수 Kotlin 파서.
 * 외부 엔티티(XXE)는 막습니다.
 */
object GpxTrackParser {
    fun parse(input: InputStream): List<GpxPoint> {
        val trk = ArrayList<GpxPoint>()
        val rte = ArrayList<GpxPoint>()
        val handler = object : DefaultHandler() {
            private var cur: Triple<Double, Double, Boolean>? = null // lat, lon, isTrk
            private var ele: Double? = null
            private var inEle = false
            private val sb = StringBuilder()

            override fun startElement(uri: String?, localName: String?, qName: String?, atts: Attributes) {
                when (val n = local(localName, qName)) {
                    "trkpt", "rtept" -> {
                        val lat = atts.getValue("lat")?.toDoubleOrNull()
                        val lon = atts.getValue("lon")?.toDoubleOrNull()
                        cur = if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) Triple(lat, lon, n == "trkpt") else null
                        ele = null
                    }
                    "ele" -> if (cur != null) { inEle = true; sb.setLength(0) }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) { if (inEle) sb.appendRange(ch, start, start + length) }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (local(localName, qName)) {
                    "ele" -> if (inEle) { ele = sb.toString().trim().toDoubleOrNull()?.takeIf { it.isFinite() && it in -500.0..9000.0 }; inEle = false }
                    "trkpt", "rtept" -> cur?.let { (la, lo, isTrk) -> (if (isTrk) trk else rte).add(GpxPoint(la, lo, ele)) }.also { cur = null }
                }
            }

            private fun local(localName: String?, qName: String?): String =
                (localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty()).substringAfter(':')
        }
        try {
            val f = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
            runCatching { f.setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            f.newSAXParser().parse(input, handler)
        } catch (e: Exception) {
            throw GpxParseException("GPX 파일을 읽을 수 없어요.", e)
        }
        val pts = if (trk.isNotEmpty()) trk else rte
        if (pts.size < 2) throw GpxParseException("경로 점이 2개 미만이에요.")
        return pts
    }
}
