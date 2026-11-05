package kr.co.nuni.gildam.ui.l10n

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 화면 글자는 strings.xml(values = 한국어, values-en = 영어)에 둡니다.
 * main 소스에 한글 문자열 리터럴이 새로 들어오면 이 테스트가 실패합니다.
 * 주석은 괜찮고, 아래 허용 목록은 화면에 직접 나오지 않는 내부 값(테스트용 라벨, DB 기본값, 데모 데이터, 지역 이름 파싱)입니다.
 */
class NoHardcodedKoreanTest {
    private val allowed = setOf(
        "data/model/Enums.kt",              // labelKo: 테스트·내부용, 화면은 ui/l10n/Labels.kt
        "data/model/GaugeMetric.kt",
        "data/gpxcompare/GpxRuggedness.kt",
        "data/list/RecordGrouper.kt",        // 화면 제목은 RecordGroup.displayTitle()
        "data/course/HikingTimeEstimator.kt",
        "data/photo/RecordPhotoMatcher.kt",
        "data/region/RegionResolver.kt",     // 한국 행정구역 접미사 파싱
        "data/repository/GildamRepository.kt", // 디버그 데모 기록
        "sync/TrackSyncMapper.kt",           // 서버 제목이 없을 때 DB 기본값
        "map/MapCameraPlanner.kt",
    )

    private val hangulLiteral = Regex("\"(?:[^\"\\\\\\n]|\\\\.)*[\\uAC00-\\uD7A3](?:[^\"\\\\\\n]|\\\\.)*\"")

    @Test
    fun noHangulStringLiteralsInMainSources() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }
        val offenders = root.listFiles()!!.filter { File(it, "src/main").isDirectory }
            .flatMap { File(it, "src/main").walkTopDown().filter { f -> f.extension == "kt" }.toList() }
            .filterNot { f -> allowed.any { f.invariantSeparatorsPath.endsWith(it) } }
            .flatMap { f ->
                stripComments(f.readText()).lineSequence().withIndex()
                    .filter { (_, line) -> hangulLiteral.containsMatchIn(line) }
                    .map { (i, line) -> "${f.relativeTo(root).invariantSeparatorsPath}:${i + 1}: ${line.trim()}" }
                    .toList()
            }
        assertTrue(
            "한글 문자열은 strings.xml 로 옮겨 주세요 (영어는 values-en):\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    /** 줄 수는 유지하면서 // 와 /* */ 주석을 지웁니다 (문자열 안의 // 는 남김). */
    private fun stripComments(src: String): String {
        val out = StringBuilder(src.length)
        var i = 0
        var inStr = false
        while (i < src.length) {
            val c = src[i]
            when {
                inStr -> {
                    out.append(c)
                    if (c == '\\' && i + 1 < src.length) { out.append(src[i + 1]); i++ }
                    else if (c == '"') inStr = false
                }
                c == '\'' -> {
                    // 문자 리터럴 ('"', '\'' 등)은 그대로 옮깁니다
                    val end = src.indexOf('\'', i + if (src.getOrNull(i + 1) == '\\') 3 else 2)
                    if (end in (i + 2)..(i + 8)) { out.append(src, i, end + 1); i = end + 1; continue }
                    out.append(c)
                }
                c == '"' -> { inStr = true; out.append(c) }
                src.startsWith("//", i) -> { while (i < src.length && src[i] != '\n') i++; continue }
                src.startsWith("/*", i) -> {
                    val end = src.indexOf("*/", i + 2).let { if (it < 0) src.length else it + 2 }
                    src.substring(i, end).forEach { if (it == '\n') out.append('\n') }
                    i = end; continue
                }
                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }
}
