package kr.co.nuni.gildam.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowRestorePolicyTest {
    private val saved = SavedFollow("course-1", 120.0, 5.0, false, "s1", "act-A")

    @Test fun stoppedFollowIsNeverRestored() {
        // X 로 그만두면 저장값 자체가 지워지므로 saved = null → 어떤 경우에도 복원 안 함
        assertFalse(FollowRestorePolicy.shouldRestore(null, recordingAlive = true, courseExists = true, currentActivityId = "act-A"))
        assertFalse(FollowRestorePolicy.shouldRestore(null, recordingAlive = true, courseExists = false))
    }

    @Test fun restoresOnlyWhileThatRecordingIsAlive() {
        assertTrue(FollowRestorePolicy.shouldRestore(saved, true, true, "act-A"))
        assertFalse(FollowRestorePolicy.shouldRestore(saved, recordingAlive = false, courseExists = true, currentActivityId = "act-A"))
        assertFalse(FollowRestorePolicy.shouldRestore(saved, true, courseExists = false, currentActivityId = "act-A"))
    }

    @Test fun newRecordingReplacesUnfinishedOneSoOldFollowIsNotRestored() {
        // 미완료 기록 A 를 두고 '새로 기록'(B)을 시작하면 id 가 달라 복원하지 않음
        assertFalse(FollowRestorePolicy.shouldRestore(saved, true, true, currentActivityId = "act-B"))
        // 아직 어떤 기록 id 도 확인되지 않은 순간에도 되살리지 않음 (id 를 모르면 안전한 쪽)
        assertFalse(FollowRestorePolicy.shouldRestore(saved, true, true, currentActivityId = null))
    }

    @Test fun startingNewRecordingClearsFollowOfPreviousUnfinishedRecording() {
        // 미완료 기록 A 에 붙은 따라 걷기 → 새 기록 B 를 시작하면 정리(CLEAR)
        assertEquals(FollowRestorePolicy.OnRecordingStart.CLEAR,
            FollowRestorePolicy.onRecordingStart(boundActivityId = "act-A", activeActivityId = "act-B"))
        // 같은 기록(이어서 기록), 아직 안 붙은 상태(코스 먼저 고르고 기록 시작) → 그대로 붙임
        assertEquals(FollowRestorePolicy.OnRecordingStart.BIND,
            FollowRestorePolicy.onRecordingStart("act-A", "act-A"))
        assertEquals(FollowRestorePolicy.OnRecordingStart.BIND,
            FollowRestorePolicy.onRecordingStart(null, "act-B"))
    }

    @Test fun legacySavedValueWithoutActivityIdStillRestores() {
        val legacy = saved.copy(activityId = null)
        assertTrue(FollowRestorePolicy.shouldRestore(legacy, true, true, currentActivityId = "act-B"))
    }
}
