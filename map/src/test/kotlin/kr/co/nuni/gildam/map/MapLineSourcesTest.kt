package kr.co.nuni.gildam.map

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kr.co.nuni.gildam.recording.LatLonPoint
import kr.co.nuni.gildam.recording.LiveTrackSimplifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

@OptIn(ExperimentalCoroutinesApi::class)
class MapLineSourcesTest {

    private fun p(i: Int) = LatLonPoint(37.5 + i * 0.00001, 127.0 + 0.0001 * sin(i / 10.0))
    private fun track(n: Int) = (0 until n).map(::p)

    @Test
    fun schedulerSkipsSameAndThrottlesGrowth() {
        val s = LineSourceScheduler(2_000)
        val k1 = LineSourceKey.of(track(10), emptyList(), null)
        assertEquals(LineSourceScheduler.Decision.Now, s.decide(k1, 0))
        s.markApplied(k1, 0)
        assertEquals(LineSourceScheduler.Decision.Skip, s.decide(k1, 500))
        val k2 = LineSourceKey.of(track(11), emptyList(), null)
        assertEquals(LineSourceScheduler.Decision.Later(1_000), s.decide(k2, 1_000))
        assertEquals(LineSourceScheduler.Decision.Now, s.decide(k2, 2_000))
    }

    @Test
    fun schedulerIsImmediateForNewCourseOrNewTrack() {
        val s = LineSourceScheduler(2_000)
        val k1 = LineSourceKey.of(track(10), emptyList(), null)
        s.markApplied(k1, 0)
        val course = track(50)
        assertEquals(LineSourceScheduler.Decision.Now, s.decide(LineSourceKey.of(track(10), course, p(3)), 100))
        assertEquals(LineSourceScheduler.Decision.Now, s.decide(LineSourceKey.of(emptyList(), emptyList(), null), 100))
        s.invalidate()
        assertEquals(LineSourceScheduler.Decision.Now, s.decide(k1, 100))
    }

    @Test
    fun buildOnlyRebuildsChangedSources() {
        val course = track(100)
        val first = MapGeoJson.build(track(10), course, p(40), null, -1)
        assertTrue(first.route != null && first.walkedCourse != null && first.remainingCourse != null)
        assertEquals(40, first.splitIndex)
        val key = LineSourceKey.of(track(10), course, p(40))
        // 경로만 늘고 코스 자른 위치는 같음 → 코스는 다시 만들지 않음
        val second = MapGeoJson.build(track(12), course, p(40), key, first.splitIndex)
        assertTrue(second.route != null)
        assertNull(second.walkedCourse)
        assertNull(second.remainingCourse)
    }

    @Test
    fun lineStringKeepsSegments() {
        val pts = track(6).mapIndexed { i, q -> if (i == 3) q.copy(isPauseBoundary = true) else q }
        val json = MapGeoJson.lineString(pts)
        assertEquals(2, Regex("\\]\\],\\[\\[").findAll(json).count() + 1)
        assertEquals(MapGeoJson.EMPTY, MapGeoJson.lineString(listOf(p(0))))
    }

    /** 1초마다 점이 들어와도 setGeoJson(apply)은 2초 간격당 한 번만 */
    @Test
    fun updaterAppliesOncePerInterval() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val applied = mutableListOf<Long>()
        val sink = object : LineSourceSink {
            override fun isReady() = true
            override fun apply(built: BuiltLineSources) { applied += scope.testScheduler.currentTime }
        }
        val updater = LineSourceUpdater(scope, sink, clock = { scope.testScheduler.currentTime }, buildDispatcher = dispatcher)
        val live = LiveTrackSimplifier()
        repeat(20) { i ->
            live.add(p(i))
            val snap = live.snapshot()
            // 같은 경로로 여러 번 재구성되는 경우도 섞음
            updater.submit(snap, emptyList(), snap.last())
            updater.submit(snap, emptyList(), snap.last())
            scope.runCurrent()
            scope.advanceTimeBy(1_000)
        }
        scope.advanceTimeBy(5_000); scope.runCurrent()
        assertTrue("적용 ${applied.size}회: $applied", applied.size in 10..11)
        applied.zipWithNext().forEach { (a, b) -> assertTrue("간격 ${b - a}", b - a >= 2_000) }
    }

    /** 2시간(7,200점) 기록 + 2만 점 코스: 메인 스레드 몫(요약 비교)과 백그라운드 몫(GeoJSON) 시간 */
    @Test
    fun twoHourTrackPerformance() {
        val live = LiveTrackSimplifier()
        // GPS 처럼 잡음 섞인 2시간 경로 (단순화가 잘 안 되는 나쁜 경우)
        val raw = (0 until 7_200).map { i ->
            LatLonPoint(37.5 + i * 0.00004, 127.0 + 0.0003 * sin(i / 7.0) + ((i * 7919) % 17 - 8) * 0.000004)
        }
        raw.forEach(live::add)
        val display = live.snapshot()
        val course = kr.co.nuni.gildam.recording.TrackSimplifier.simplifyToLimit(track(20_000)).first
        val s = LineSourceScheduler()
        repeat(200) { MapGeoJson.build(display, course, display.last(), null, -1) } // 워밍업

        var mainWorst = 0L
        var t = 0L
        for (i in 0 until 1_000) {
            val t0 = System.nanoTime()
            val k = LineSourceKey.of(display, course, display[i % display.size])
            s.decide(k, t); s.markApplied(k, t)
            val dt = System.nanoTime() - t0
            if (dt > mainWorst) mainWorst = dt
            t += 2_000
        }
        var bgTotal = 0L
        repeat(50) {
            val t0 = System.nanoTime()
            MapGeoJson.build(display, course, display.last(), null, -1)
            bgTotal += System.nanoTime() - t0
        }
        var oldTotal = 0L
        repeat(20) {
            val t0 = System.nanoTime()
            // 예전 방식: 원본 전체 경로·원본 코스 전체를 매 갱신마다 문자열로
            MapGeoJson.lineString(raw); MapGeoJson.lineString(track(20_000))
            oldTotal += System.nanoTime() - t0
        }
        println("PERF map display=%d course=%d main(decide) worst=%.3fms bg(build) avg=%.2fms old(raw build) avg=%.2fms"
            .format(display.size, course.size, mainWorst / 1e6, bgTotal / 50 / 1e6, oldTotal / 20 / 1e6))
        assertTrue(display.size <= kr.co.nuni.gildam.recording.TrackSimplifier.MAX_DISPLAY_POINTS)
        assertTrue("main ${mainWorst / 1e6}ms", mainWorst < 16_000_000L)
    }
}
