package kr.co.nuni.gildam.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import kr.co.nuni.gildam.data.diagnostics.ExitKind
import kr.co.nuni.gildam.data.diagnostics.ExitLogEntry
import kr.co.nuni.gildam.data.diagnostics.ExitLogFormat
import kr.co.nuni.gildam.ui.theme.GildamTheme
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

/** 설정 > 앱 종료 기록 (가짜 기록, 한국어) */
class AppExitLogPreviewTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(locale = "ko"),
        theme = "android:Theme.Material.Light.NoActionBar",
        showSystemUi = false
    )

    @Test
    fun exitLogList() = paparazzi.snapshot { Screen() }

    @Test
    fun exitLogDetail() = paparazzi.snapshot { Screen(initialSelectedId = sampleEntries().first().id) }

    @Test
    fun exitLogEmpty() = paparazzi.snapshot { Screen(entries = emptyList()) }

    @Composable
    private fun Screen(entries: List<ExitLogEntry> = sampleEntries(), initialSelectedId: String? = null) {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            GildamTheme {
                AppExitLogScreen(
                    entries = entries,
                    onNavigateBack = {},
                    zone = ZoneId.of("Asia/Seoul"),
                    initialSelectedId = initialSelectedId
                )
            }
        }
    }
}

internal fun sampleEntries(): List<ExitLogEntry> {
    fun entry(time: Long, kind: ExitKind, reason: String, summary: String, body: String = ""): ExitLogEntry {
        val id = ExitLogFormat.fileName(time, kind)
        val text = ExitLogFormat.encode(
            listOf(
                ExitLogFormat.KEY_TIME to time.toString(),
                ExitLogFormat.KEY_KIND to kind.name,
                ExitLogFormat.KEY_REASON to reason,
                ExitLogFormat.KEY_SUMMARY to summary,
                "importance" to "125",
                "pss_kb" to "409600"
            ),
            body
        )
        return ExitLogFormat.decode(id, text)!!
    }
    return listOf(
        entry(
            1_763_419_658_000L, ExitKind.ANR, "ANR", "Input dispatching timed out (10000ms)",
            "\"main\" prio=5 tid=1 Native\n  native: mbgl::android::MapRenderer::update\n  at android.os.MessageQueue.next(MessageQueue.java:335)\n  at android.os.Looper.loop(Looper.java:183)"
        ),
        entry(1_763_388_480_000L, ExitKind.EXCESSIVE_RESOURCE_USAGE, "EXCESSIVE_RESOURCE_USAGE", "excessive cpu usage"),
        entry(1_763_334_100_000L, ExitKind.USER_REQUESTED, "USER_REQUESTED", ""),
        entry(1_763_300_000_000L, ExitKind.CRASH, "UNCAUGHT_EXCEPTION", "java.lang.IllegalStateException: boom", "java.lang.IllegalStateException: boom\n  at kr.co.nuni.gildam.Foo.bar(Foo.kt:1)")
    )
}
