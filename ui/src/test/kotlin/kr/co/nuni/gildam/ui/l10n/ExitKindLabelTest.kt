package kr.co.nuni.gildam.ui.l10n

import kr.co.nuni.gildam.data.diagnostics.ExitKind
import kr.co.nuni.gildam.ui.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 앱 종료 기록: 종료 이유 → 한국어/영어 설명 */
class ExitKindLabelTest {
    private fun strings(dir: String): Map<String, String> {
        val f = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "ui/src/main/res/$dir/strings_l10n.xml") }.firstOrNull { it.exists() }
            ?: File("src/main/res/$dir/strings_l10n.xml")
        return Regex("<string name=\"([^\"]+)\">([^<]*)</string>").findAll(f.readText(Charsets.UTF_8))
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    @Test
    fun everyKindHasKoreanAndEnglishText() {
        val ko = strings("values")
        val en = strings("values-en")
        ExitKind.entries.forEach { kind ->
            val key = "exit_reason_" + kind.name.lowercase()
            assertTrue("ko $key", !ko[key].isNullOrBlank())
            assertTrue("en $key", !en[key].isNullOrBlank())
        }
        assertEquals("화면이 응답하지 않아 닫힘", ko["exit_reason_anr"])
        assertEquals("메모리 부족으로 시스템이 종료", ko["exit_reason_low_memory"])
        assertEquals("배터리·CPU를 너무 많이 써서 시스템이 종료", ko["exit_reason_excessive_resource_usage"])
        assertEquals("직접 종료", ko["exit_reason_user_requested"])
        assertEquals("오류로 종료", ko["exit_reason_crash"])
    }

    @Test
    fun labelResMatchesKeys() {
        assertEquals(R.string.exit_reason_anr, ExitKind.ANR.labelRes())
        assertEquals(R.string.exit_reason_crash, ExitKind.CRASH.labelRes())
        assertEquals(R.string.exit_reason_low_memory, ExitKind.LOW_MEMORY.labelRes())
        assertEquals(R.string.exit_reason_excessive_resource_usage, ExitKind.EXCESSIVE_RESOURCE_USAGE.labelRes())
        assertEquals(R.string.exit_reason_user_requested, ExitKind.USER_REQUESTED.labelRes())
        assertEquals(R.string.exit_reason_other, ExitKind.OTHER.labelRes())
    }
}
