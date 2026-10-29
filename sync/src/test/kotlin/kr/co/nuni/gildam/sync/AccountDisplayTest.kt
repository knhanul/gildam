package kr.co.nuni.gildam.sync

import kotlinx.serialization.json.Json
import kr.co.nuni.gildam.sync.api.MeResponseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AccountDisplayTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun responseWithOnlyDisplayNameDeserializesSafely() {
        val dto = json.decodeFromString(
            MeResponseDto.serializer(),
            """{"sub":"internal-account-id","display_name":"누니 회원","track_count":3}"""
        )

        assertEquals("누니 회원", dto.displayName)
        assertNull(dto.nickname)
        assertEquals(3, AccountDisplay.from(dto).trackCount)
    }

    @Test
    fun responseWithDisplayNameAndNicknameDeserializes() {
        val dto = json.decodeFromString(
            MeResponseDto.serializer(),
            """{"sub":"internal-account-id","nickname":"길손","display_name":"길손","track_count":7}"""
        )
        val display = AccountDisplay.from(dto)

        assertEquals("길손", display.displayName)
        assertEquals("길손", display.nickname)
        assertEquals(7, display.trackCount)
    }

    @Test
    fun olderResponseWithoutNewFieldsUsesSafeDisplayFallback() {
        val dto = json.decodeFromString(
            MeResponseDto.serializer(),
            """{"sub":"internal-account-id","track_count":2}"""
        )
        val display = AccountDisplay.from(dto)

        assertEquals("누니 회원", display.displayName)
        assertNull(display.nickname)
        assertEquals(2, display.trackCount)
    }

    @Test
    fun blankDisplayNameUsesFallbackAndInternalIdIsNotExposed() {
        val sub = "3f1c2a9e-7b6d-4e0f-9a21-5c8b7d6e4f30"
        val dto = MeResponseDto(sub = sub, displayName = "   ", nickname = "  ")
        val display = AccountDisplay.from(dto)

        assertEquals("누니 회원", display.displayName)
        assertNull(display.nickname)
        assertFalse(display.toString().contains(sub))
    }
}
