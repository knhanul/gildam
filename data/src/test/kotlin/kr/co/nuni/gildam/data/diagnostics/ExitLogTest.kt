package kr.co.nuni.gildam.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExitLogTest {

    @Test
    fun reasonCodesMapToKinds() {
        assertEquals(ExitKind.ANR, ExitKind.fromReasonCode(6))
        assertEquals(ExitKind.CRASH, ExitKind.fromReasonCode(4))
        assertEquals(ExitKind.CRASH, ExitKind.fromReasonCode(5))
        assertEquals(ExitKind.LOW_MEMORY, ExitKind.fromReasonCode(3))
        assertEquals(ExitKind.EXCESSIVE_RESOURCE_USAGE, ExitKind.fromReasonCode(9))
        assertEquals(ExitKind.USER_REQUESTED, ExitKind.fromReasonCode(10))
        assertEquals(ExitKind.USER_REQUESTED, ExitKind.fromReasonCode(11))
        assertEquals(ExitKind.APP_UPDATED, ExitKind.fromReasonCode(16))
        assertEquals(ExitKind.OTHER, ExitKind.fromReasonCode(99))
        assertEquals("EXCESSIVE_RESOURCE_USAGE", ExitKind.reasonName(9))
        assertEquals("UNKNOWN(99)", ExitKind.reasonName(99))
    }

    @Test
    fun retentionKeepsNewestTenPerGroup() {
        val traces = (1..13).map { ExitLogFormat.fileName(it * 1000L, ExitKind.ANR) }
        val exits = (1..4).map { ExitLogFormat.fileName(it * 1000L, ExitKind.USER_REQUESTED) }
        val delete = ExitLogRetention.filesToDelete(traces + exits + "last_exit_timestamp")
        assertEquals(traces.take(3), delete)
    }

    @Test
    fun encodeDecodeRoundTripWithoutCoordinates() {
        val text = ExitLogFormat.encode(
            listOf(
                ExitLogFormat.KEY_TIME to "1763419958000",
                ExitLogFormat.KEY_KIND to ExitKind.ANR.name,
                ExitLogFormat.KEY_REASON to "ANR",
                ExitLogFormat.KEY_SUMMARY to "Input dispatching timed out"
            ),
            "at foo(Bar.kt:12)\nlat=37.512345 lon=126.987654 addr 0x7fa1"
        )
        val e = ExitLogFormat.decode("trace-x.txt", text)!!
        assertEquals(1763419958000L, e.timestampMs)
        assertEquals(ExitKind.ANR, e.kind)
        assertEquals("Input dispatching timed out", e.summary)
        assertFalse(e.content.contains("37.512345"))
        assertFalse(e.content.contains("126.987654"))
        assertTrue(e.content.contains("Bar.kt:12"))
        assertTrue(e.content.contains("0x7fa1"))
    }

    @Test
    fun anrTraceMainThreadFirstAndLimited() {
        val trace = "----- pid 1 -----\n\n\"Signal Catcher\" daemon\n  at a\n\n\"main\" prio=5 tid=1 Native\n  at mbgl.MapRenderer.update\n\n\"Thread-2\"\n  at b\n"
        val r = AnrTraceTrimmer.trim(trace)
        assertTrue(r.startsWith("\"main\""))
        assertTrue(r.contains("Signal Catcher"))
        val big = AnrTraceTrimmer.trim("x".repeat(200_000))
        assertTrue(big.length <= AnrTraceTrimmer.MAX_CHARS + 32)
    }
}
