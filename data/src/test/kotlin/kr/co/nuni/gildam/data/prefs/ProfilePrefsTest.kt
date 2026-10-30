package kr.co.nuni.gildam.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfilePrefsTest {
    @Test fun nicknameIsTrimmedAndLimitedTo20() {
        assertEquals("길담", ProfilePrefs.normalize("  길담 "))
        assertNull(ProfilePrefs.normalize("   "))
        assertEquals("가".repeat(20), ProfilePrefs.normalize("가".repeat(20)))
        assertNull(ProfilePrefs.normalize("가".repeat(21)))
    }
}
