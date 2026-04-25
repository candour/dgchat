package com.messark.dgchat

import org.junit.Assert.*
import org.junit.Test

class NicknameUtilsTest {

    @Test
    fun testValidNicknames() {
        assertTrue(NicknameUtils.isValid("user"))
        assertTrue(NicknameUtils.isValid("user-123"))
        assertTrue(NicknameUtils.isValid("你好"))
        assertTrue(NicknameUtils.isValid("a".repeat(32)))
        assertTrue(NicknameUtils.isValid("User-Name-123"))
    }

    @Test
    fun testInvalidNicknames() {
        assertFalse(NicknameUtils.isValid("")) // Too short
        assertFalse(NicknameUtils.isValid("a".repeat(33))) // Too long
        assertFalse(NicknameUtils.isValid("user_name")) // Underscore not allowed
        assertFalse(NicknameUtils.isValid("user name")) // Space not allowed
        assertFalse(NicknameUtils.isValid("user@domain")) // Special char not allowed
        assertFalse(NicknameUtils.isValid("user.name")) // Dot not allowed
    }

    @Test
    fun testGenerateDefaultNickname() {
        val nickname = NicknameUtils.generateDefaultNickname()
        assertTrue(nickname.startsWith("androiduser"))
        assertEquals(11 + 8, nickname.length)
        assertTrue(NicknameUtils.isValid(nickname))
    }
}
