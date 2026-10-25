package kr.co.nuni.gildam.data.gpxcompare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.sin

class GpxCompareTest {
    private val mPerDegLat = 111_195.08

    /** 북쪽으로 일직선, spacing m 간격, 고도 함수 */
    private fun line(lengthM: Double, spacing: Double = 10.0, ele: (Double) -> Double?): List<GpxPoint> =
        (0..(lengthM / spacing).toInt()).map { i -> val d = i * spacing; GpxPoint(37.5 + d / mPerDegLat, 127.0, ele(d)) }

    private fun gpx(points: List<GpxPoint>, tag: String = "trkpt") = buildString {
        append("""<?xml version="1.0"?><gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1"><trk><name>비밀 이름</name><trkseg>""")
        points.forEach { p -> append("<$tag lat=\"${p.lat}\" lon=\"${p.lon}\">"); p.ele?.let { append("<ele>$it</ele>") }; append("<time>2026-10-25T00:00:00Z</time></$tag>") }
        append("</trkseg></trk></gpx>")
    }.byteInputStream()

    @Test fun parsesTrackPointsWithNamespaceAndElevation() {
        val pts = GpxTrackParser.parse(gpx(line(100.0) { 10.0 + it / 10 }))
        assertEquals(11, pts.size)
        assertEquals(10.0, pts[0].ele!!, 1e-9); assertEquals(20.0, pts[10].ele!!, 1e-9)
    }

    @Test fun parsesRoutePointsWhenNoTrack() {
        val s = """<gpx><rte><rtept lat="37.0" lon="127.0"/><rtept lat="37.001" lon="127.0"/></rte></gpx>"""
        val pts = GpxTrackParser.parse(s.byteInputStream())
        assertEquals(2, pts.size); assertNull(pts[0].ele)
    }

    @Test fun rejectsBrokenOrTooShort() {
        listOf("not xml", "<gpx><trk><trkseg><trkpt lat=\"1\" lon=\"2\"/></trkseg></trk></gpx>").forEach {
            try { GpxTrackParser.parse(it.byteInputStream()); fail() } catch (_: GpxParseException) {}
        }
    }

    @Test fun rejectsDoctypeEntities() {
        val xxe = """<?xml version="1.0"?><!DOCTYPE gpx [<!ENTITY x SYSTEM "file:///etc/passwd">]><gpx><trk><trkseg><trkpt lat="1" lon="2"><ele>&x;</ele></trkpt><trkpt lat="1.1" lon="2"/></trkseg></trk></gpx>"""
        try { GpxTrackParser.parse(xxe.byteInputStream()); fail() } catch (_: GpxParseException) {}
    }

    @Test fun haversineOneKmNorth() {
        assertEquals(1000.0, GpxRuggedness.haversineM(GpxPoint(37.5, 127.0, null), GpxPoint(37.5 + 1000 / mPerDegLat, 127.0, null)), 1.0)
    }

    @Test fun flatWalkIsEasyAndNoiseIsIgnored() {
        // 3km 평지, ±1.5m GPS 고도 잡음
        val r = GpxRuggedness.analyze("평지", line(3000.0) { 20.0 + 1.5 * sin(it / 7.0) })
        assertEquals(3000.0, r.distanceM, 5.0)
        assertTrue("ascent=${r.ascentM}", r.ascentM < 5)
        assertEquals(0.0, r.steepRatio, 1e-9)
        assertEquals(RuggednessGrade.EASY, r.grade)
        assertTrue(r.hasElevation)
    }

    @Test fun steadyClimbComputesAscentGradeAndSteep() {
        // 2km 동안 20% 오르막(400m 상승) 후 2km 동안 평지
        val r = GpxRuggedness.analyze("산길", line(4000.0) { if (it <= 2000) it * 0.2 else 400.0 })
        assertEquals(400.0, r.ascentM, 25.0)
        assertTrue(r.descentM < 5)
        assertEquals(20.0, r.maxUphillGradePct, 1.5)
        assertEquals(2000.0, r.steepDistanceM, 120.0)
        assertEquals(0.5, r.steepRatio, 0.04)
        assertEquals(400.0, r.maxElevationM!!, 3.0); assertEquals(0.0, r.minElevationM!!, 3.0)
        // 등가거리 = 4 + 400/100 = 8, 점수 = 8 + 2km*2 = 12
        assertEquals(8.0, r.equivalentKm, 0.3)
        assertEquals(12.0, r.score, 0.6)
        assertEquals(GpxRuggedness.gradeFor(r.score), r.grade)
        val steepBucket = r.gradeBuckets.first { it.fromPct == 15.0 }
        assertEquals(0.5, steepBucket.ratio, 0.04)
        assertEquals(1.0, r.gradeBuckets.sumOf { it.ratio }, 1e-6)
    }

    @Test fun descentIsCounted() {
        val r = GpxRuggedness.analyze("내리막", line(1000.0) { 300.0 - it * 0.1 })
        assertEquals(100.0, r.descentM, 8.0)
        assertEquals(10.0, r.maxDownhillGradePct, 1.0)
    }

    @Test fun noElevationIsFlagged() {
        val r = GpxRuggedness.analyze("고도없음", line(2000.0) { null })
        assertFalse(r.hasElevation)
        assertEquals(2.0, r.score, 0.05)
        assertTrue(r.profile.isEmpty())
    }

    @Test fun partialElevationBelowCoverageIsFlagged() {
        val r = GpxRuggedness.analyze("부분", line(1000.0) { d -> if (d < 200) 10.0 else null })
        assertFalse(r.hasElevation)
    }

    @Test fun profileIsBoundedAndEndsAtTotal() {
        val r = GpxRuggedness.analyze("긴길", line(20_000.0, spacing = 20.0) { 100 + 50 * sin(it / 1000.0) })
        assertTrue(r.profile.size <= 170)
        assertEquals(r.distanceM / 1000, r.profile.last().distanceKm, 0.02)
    }

    @Test fun gradeThresholds() {
        assertEquals(RuggednessGrade.EASY, GpxRuggedness.gradeFor(5.9))
        assertEquals(RuggednessGrade.MODERATE, GpxRuggedness.gradeFor(6.0))
        assertEquals(RuggednessGrade.SOMEWHAT_HARD, GpxRuggedness.gradeFor(12.0))
        assertEquals(RuggednessGrade.HARD, GpxRuggedness.gradeFor(18.0))
        assertEquals(RuggednessGrade.VERY_HARD, GpxRuggedness.gradeFor(28.0))
    }
}
