package kr.co.nuni.gildam.data.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 앱 종료 기록 (원인 확인용). 앱 내부 저장소 `crash/` 폴더에만 두고 서버로 보내지 않습니다.
 * - 앱 시작 때 [recordHistoricalExits]: 시스템이 남긴 종료 이유(Android 11+) 중 새 것만 저장, ANR 은 트레이스 앞부분도
 * - [installCrashHandler]: 처리 안 된 예외 스택을 파일로 저장한 뒤 원래 처리기로 넘김
 * - 위치 좌표는 남기지 않음
 */
@Singleton
class ExitLogStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir: File get() = File(context.filesDir, DIR_NAME).apply { mkdirs() }
    private val lastSeenFile: File get() = File(dir, LAST_SEEN_FILE)

    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { writeCrash(thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** 시스템 종료 기록 중 지난번 이후 새로 생긴 것만 저장. 백그라운드 스레드에서 부르세요. */
    @Synchronized
    fun recordHistoricalExits() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            val am = context.getSystemService(ActivityManager::class.java) ?: return
            val infos = am.getHistoricalProcessExitReasons(context.packageName, 0, MAX_SYSTEM_EXITS)
            val lastSeen = lastSeenFile.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: 0L
            val fresh = infos.filter { it.timestamp > lastSeen }.sortedBy { it.timestamp }
            fresh.forEach { writeSystemExit(it) }
            fresh.maxOfOrNull { it.timestamp }?.let { lastSeenFile.writeText(it.toString()) }
            prune()
        }.onFailure { Log.w(TAG, "exit reasons read failed", it) }
    }

    /** 최근 것부터 */
    fun entries(): List<ExitLogEntry> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }.orEmpty()
            .mapNotNull { f -> runCatching { ExitLogFormat.decode(f.name, f.readText()) }.getOrNull() }
            .sortedByDescending { it.timestampMs }

    private fun writeSystemExit(info: ApplicationExitInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val kind = ExitKind.fromReasonCode(info.reason)
        val body = if (info.reason == ApplicationExitInfo.REASON_ANR) {
            runCatching {
                info.traceInputStream?.use { input ->
                    // 앞부분만 읽음 (메인 스레드 스택이 앞쪽에 있음)
                    val buf = ByteArray(AnrTraceTrimmer.MAX_CHARS * 2)
                    var n = 0
                    while (n < buf.size) { val r = input.read(buf, n, buf.size - n); if (r < 0) break; n += r }
                    AnrTraceTrimmer.trim(String(buf, 0, n, Charsets.UTF_8))
                }
            }.getOrNull().orEmpty()
        } else ""
        val headers = listOf(
            ExitLogFormat.KEY_TIME to info.timestamp.toString(),
            ExitLogFormat.KEY_KIND to kind.name,
            ExitLogFormat.KEY_REASON to ExitKind.reasonName(info.reason),
            ExitLogFormat.KEY_SUMMARY to info.description.orEmpty(),
            "status" to info.status.toString(),
            "importance" to info.importance.toString(),
            "pss_kb" to info.pss.toString(),
            "rss_kb" to info.rss.toString(),
            "process" to info.processName.orEmpty(),
            "app_version" to appVersion()
        )
        File(dir, ExitLogFormat.fileName(info.timestamp, kind, "-${info.pid}")).writeText(ExitLogFormat.encode(headers, body))
    }

    private fun writeCrash(thread: Thread, error: Throwable) {
        val now = System.currentTimeMillis()
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val headers = listOf(
            ExitLogFormat.KEY_TIME to now.toString(),
            ExitLogFormat.KEY_KIND to ExitKind.CRASH.name,
            ExitLogFormat.KEY_REASON to UNCAUGHT_REASON,
            ExitLogFormat.KEY_SUMMARY to CoordinateScrubber.scrub("${error.javaClass.name}: ${error.message.orEmpty()}").take(300),
            "thread" to thread.name,
            "app_version" to appVersion()
        )
        File(dir, ExitLogFormat.fileName(now, ExitKind.CRASH, "-uncaught"))
            .writeText(ExitLogFormat.encode(headers, stack.take(AnrTraceTrimmer.MAX_CHARS)))
        prune()
    }

    private fun prune() {
        val names = dir.list().orEmpty().toList()
        ExitLogRetention.filesToDelete(names).forEach { File(dir, it).delete() }
    }

    private fun appVersion(): String = runCatching {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
        "${pi.versionName} ($code)"
    }.getOrDefault("")

    companion object {
        const val DIR_NAME = "crash"
        const val UNCAUGHT_REASON = "UNCAUGHT_EXCEPTION"
        private const val LAST_SEEN_FILE = "last_exit_timestamp"
        private const val MAX_SYSTEM_EXITS = 10
        private const val TAG = "ExitLogStore"
    }
}
