package com.messark.dgchat

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageUtilsTest {

    @Test
    fun testSplitSimple() {
        val text = "Hello world from the other side"
        val limit = 10
        val parts = MessageUtils.splitMessage(text, limit)
        // "Hello " (6)
        // "world " (6)
        // "from the " (9)
        // "other side" (10)
        assertEquals(listOf("Hello ", "world ", "from the ", "other side"), parts)
    }

    @Test
    fun testSplitNoSpaces() {
        val text = "abcdefghij"
        val limit = 5
        val parts = MessageUtils.splitMessage(text, limit)
        assertEquals(listOf("abcde", "fghij"), parts)
    }

    @Test
    fun testSplitWithAnsi() {
        val redA = "\u001b[31mA\u001b[0m" // 10 bytes
        val text = "Start $redA End"
        val limit = 12
        val parts = MessageUtils.splitMessage(text, limit)
        // "Start " (6)
        // redA + " " (11)
        // "End" (3)
        assertEquals(listOf("Start ", "$redA ", "End"), parts)
    }

    @Test
    fun testSplitUTF8() {
        val text = "😀😀😀" // Each is 4 bytes
        val limit = 6
        val parts = MessageUtils.splitMessage(text, limit)
        assertEquals(listOf("😀", "😀", "😀"), parts)
    }
}
