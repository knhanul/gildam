package kr.co.nuni.gildam.data.diagnostics

/**
 * 앱이 왜 끝났는지 (원인 확인용, 이 기기에만 저장). 화면 글자는 ui/l10n/Labels.kt 에서 바꿉니다.
 * 코드 값은 android.app.ApplicationExitInfo.REASON_* 와 같습니다.
 */
enum class ExitKind {
    ANR,
    CRASH,
    LOW_MEMORY,
    EXCESSIVE_RESOURCE_USAGE,
    USER_REQUESTED,
    EXIT_SELF,
    SIGNALED,
    APP_UPDATED,
    OTHER;

    /** 스택·트레이스를 남기는 종류 (최근 [ExitLogRetention.MAX_PER_GROUP] 개만 유지) */
    val hasTrace: Boolean get() = this == ANR || this == CRASH

    companion object {
        fun fromReasonCode(code: Int): ExitKind = when (code) {
            REASON_ANR -> ANR
            REASON_CRASH, REASON_CRASH_NATIVE -> CRASH
            REASON_LOW_MEMORY -> LOW_MEMORY
            REASON_EXCESSIVE_RESOURCE_USAGE -> EXCESSIVE_RESOURCE_USAGE
            REASON_USER_REQUESTED, REASON_USER_STOPPED -> USER_REQUESTED
            REASON_EXIT_SELF -> EXIT_SELF
            REASON_SIGNALED -> SIGNALED
            REASON_PACKAGE_UPDATED -> APP_UPDATED
            else -> OTHER
        }

        /** 사람이 읽는 이름 (파일에 남기는 영어 코드 이름) */
        fun reasonName(code: Int): String = REASON_NAMES[code] ?: "UNKNOWN($code)"

        const val REASON_UNKNOWN = 0
        const val REASON_EXIT_SELF = 1
        const val REASON_SIGNALED = 2
        const val REASON_LOW_MEMORY = 3
        const val REASON_CRASH = 4
        const val REASON_CRASH_NATIVE = 5
        const val REASON_ANR = 6
        const val REASON_INITIALIZATION_FAILURE = 7
        const val REASON_PERMISSION_CHANGE = 8
        const val REASON_EXCESSIVE_RESOURCE_USAGE = 9
        const val REASON_USER_REQUESTED = 10
        const val REASON_USER_STOPPED = 11
        const val REASON_DEPENDENCY_DIED = 12
        const val REASON_OTHER = 13
        const val REASON_FREEZER = 14
        const val REASON_PACKAGE_STATE_CHANGE = 15
        const val REASON_PACKAGE_UPDATED = 16

        private val REASON_NAMES = mapOf(
            REASON_UNKNOWN to "UNKNOWN", REASON_EXIT_SELF to "EXIT_SELF", REASON_SIGNALED to "SIGNALED",
            REASON_LOW_MEMORY to "LOW_MEMORY", REASON_CRASH to "CRASH", REASON_CRASH_NATIVE to "CRASH_NATIVE",
            REASON_ANR to "ANR", REASON_INITIALIZATION_FAILURE to "INITIALIZATION_FAILURE",
            REASON_PERMISSION_CHANGE to "PERMISSION_CHANGE", REASON_EXCESSIVE_RESOURCE_USAGE to "EXCESSIVE_RESOURCE_USAGE",
            REASON_USER_REQUESTED to "USER_REQUESTED", REASON_USER_STOPPED to "USER_STOPPED",
            REASON_DEPENDENCY_DIED to "DEPENDENCY_DIED", REASON_OTHER to "OTHER", REASON_FREEZER to "FREEZER",
            REASON_PACKAGE_STATE_CHANGE to "PACKAGE_STATE_CHANGE", REASON_PACKAGE_UPDATED to "PACKAGE_UPDATED"
        )
    }
}

/** 저장된 종료 기록 한 건 */
data class ExitLogEntry(
    /** 파일 이름 (목록에서 고를 때 id 로 씀) */
    val id: String,
    val timestampMs: Long,
    val kind: ExitKind,
    /** ApplicationExitInfo reason 이름 또는 "UNCAUGHT_EXCEPTION" */
    val reasonName: String,
    /** 한 줄 요약 (시스템 설명 또는 예외 이름) */
    val summary: String,
    /** 파일 전체 내용 (복사·공유용) */
    val content: String
)

/**
 * 종료 기록 파일 형식: 머리글 `key: value` 줄들, 빈 줄, 본문(스택·트레이스).
 * 위치 좌표는 남기지 않습니다 ([CoordinateScrubber]).
 */
object ExitLogFormat {
    const val KEY_TIME = "time"
    const val KEY_KIND = "kind"
    const val KEY_REASON = "reason"
    const val KEY_SUMMARY = "summary"

    fun encode(headers: List<Pair<String, String>>, body: String): String = buildString {
        headers.forEach { (k, v) -> append(k).append(": ").append(CoordinateScrubber.scrub(v.replace('\n', ' '))).append('\n') }
        append('\n')
        append(CoordinateScrubber.scrub(body))
    }

    fun decode(id: String, text: String): ExitLogEntry? {
        val headerEnd = text.indexOf("\n\n").let { if (it < 0) text.length else it }
        val headers = text.substring(0, headerEnd).lineSequence()
            .mapNotNull { line -> line.indexOf(": ").takeIf { it > 0 }?.let { line.substring(0, it) to line.substring(it + 2) } }
            .toMap()
        val time = headers[KEY_TIME]?.toLongOrNull() ?: return null
        val kind = headers[KEY_KIND]?.let { k -> ExitKind.entries.firstOrNull { it.name == k } } ?: ExitKind.OTHER
        return ExitLogEntry(id, time, kind, headers[KEY_REASON].orEmpty(), headers[KEY_SUMMARY].orEmpty(), text)
    }

    /** 파일 이름: 시각이 앞이라 이름순 = 시간순. 트레이스 있는 종류는 trace- 로 시작 */
    fun fileName(timestampMs: Long, kind: ExitKind, suffix: String = ""): String =
        (if (kind.hasTrace) "trace-" else "exit-") + "%013d".format(timestampMs) + "-" + kind.name.lowercase() + suffix + ".txt"
}

/** 보관 개수 정리 (순수 함수) */
object ExitLogRetention {
    /** 크래시·ANR(트레이스) 과 그 밖의 종료 각각 최근 이만큼만 */
    const val MAX_PER_GROUP = 10

    /** 지울 파일 이름들: 그룹(trace-/exit-)마다 이름(=시각) 기준 오래된 것부터 */
    fun filesToDelete(fileNames: List<String>, max: Int = MAX_PER_GROUP): List<String> =
        fileNames.filter { it.endsWith(".txt") }
            .groupBy { it.substringBefore('-') }
            .values.flatMap { group -> group.sorted().dropLast(max) }
}

/** ANR 트레이스 다듬기: 메인 스레드 스택을 맨 앞에 두고 최대 [maxChars] 까지 */
object AnrTraceTrimmer {
    const val MAX_CHARS = 64 * 1024

    fun trim(trace: String, maxChars: Int = MAX_CHARS): String {
        val mainStart = Regex("(?m)^\"main\"").find(trace)?.range?.first
        val ordered = if (mainStart == null) trace else {
            val after = trace.indexOf("\n\n", mainStart).let { if (it < 0) trace.length else it }
            val main = trace.substring(mainStart, after)
            main + "\n\n----- other threads -----\n" + trace.substring(0, mainStart) + trace.substring(after)
        }
        return if (ordered.length <= maxChars) ordered else ordered.substring(0, maxChars) + "\n... (truncated)"
    }
}

/**
 * 위치 좌표처럼 보이는 숫자(소수점 아래 4자리 이상)를 지웁니다. 종료 기록에는 위치를 남기지 않음.
 * (메모리 주소는 16진수라 영향 없음)
 */
object CoordinateScrubber {
    private val decimal = Regex("(?<![\\w.])-?\\d{1,3}\\.\\d{4,}(?![\\w.])")
    fun scrub(text: String): String = decimal.replace(text, "#.####")
}
