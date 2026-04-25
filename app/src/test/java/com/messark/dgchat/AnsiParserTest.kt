package com.messark.dgchat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnsiParserTest {

    @Test
    fun testPlainText() {
        val input = "Hello World"
        val result = AnsiParser.parseAnsi(input)
        assertEquals("Hello World", result.text)
        assertEquals(0, result.spanStyles.size)
    }

    @Test
    fun testBasicColors() {
        val input = "\u001b[31mRed Text\u001b[0m"
        val result = AnsiParser.parseAnsi(input)
        assertEquals("Red Text", result.text)
        assertEquals(1, result.spanStyles.size)
        // Red (Red 500)
        assertEquals(Color(0xFFF44336), result.spanStyles[0].item.color)
    }

    @Test
    fun testBoldItalicUnderline() {
        val input = "\u001b[1mBold\u001b[0m \u001b[3mItalic\u001b[0m \u001b[4mUnderline\u001b[0m"
        val result = AnsiParser.parseAnsi(input)
        assertEquals("Bold Italic Underline", result.text)

        // Find styles for each part
        val boldPart = result.spanStyles.find { result.text.substring(it.start, it.end) == "Bold" }
        val italicPart = result.spanStyles.find { result.text.substring(it.start, it.end) == "Italic" }
        val underlinePart = result.spanStyles.find { result.text.substring(it.start, it.end) == "Underline" }

        assertEquals(FontWeight.Bold, boldPart?.item?.fontWeight)
        assertEquals(FontStyle.Italic, italicPart?.item?.fontStyle)
        assertEquals(TextDecoration.Underline, underlinePart?.item?.textDecoration)
    }

    @Test
    fun test256Colors() {
        val input = "\u001b[38;5;208mOrange\u001b[0m"
        val result = AnsiParser.parseAnsi(input)
        assertEquals("Orange", result.text)
        // 208 is Orange-ish. Let's see if it has a color.
        val orangePart = result.spanStyles.find { result.text.substring(it.start, it.end) == "Orange" }
        assertEquals(true, orangePart?.item?.color != Color.Unspecified)
    }

    @Test
    fun testRGBColors() {
        val input = "\u001b[38;2;100;150;200mRGB\u001b[0m"
        val result = AnsiParser.parseAnsi(input)
        assertEquals("RGB", result.text)
        val rgbPart = result.spanStyles.find { result.text.substring(it.start, it.end) == "RGB" }
        assertEquals(Color(100f / 255f, 150f / 255f, 200f / 255f), rgbPart?.item?.color)
    }

    @Test
    fun testBackgroundColors() {
        val input = "\u001b[42mGreen BG\u001b[0m"
        val result = AnsiParser.parseAnsi(input)
        assertEquals("Green BG", result.text)
        val bgPart = result.spanStyles.find { result.text.substring(it.start, it.end) == "Green BG" }
        // Green (Green 500)
        assertEquals(Color(0xFF4CAF50), bgPart?.item?.background)
    }

    @Test
    fun testToAnsiString() {
        val builder = AnnotatedString.Builder("Hello World")
        builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), 0, 5)
        builder.addStyle(SpanStyle(color = Color(0xFFF44336)), 6, 11)
        val annotated = builder.toAnnotatedString()

        val ansi = AnsiParser.toAnsiString(annotated)
        // Expected: "\u001b[0m\u001b[1mHello\u001b[0m \u001b[31mWorld\u001b[0m"
        // Note: my implementation always starts with [0m if it's different from initial (empty)
        assertTrue(ansi.contains("\u001b[1mHello"))
        assertTrue(ansi.contains("\u001b[31mWorld"))

        // Round trip
        val back = AnsiParser.parseAnsi(ansi)
        assertEquals(annotated.text, back.text)
        assertEquals(2, back.spanStyles.size)
    }

    @Test
    fun testAnsiOptimization() {
        val builder = AnnotatedString.Builder("red")
        builder.addStyle(SpanStyle(color = Color(0xFFF44336)), 0, 3)
        val annotated = builder.toAnnotatedString()

        val ansi = AnsiParser.toAnsiString(annotated)
        println("DEBUG: testAnsiOptimization output: ${ansi.replace("\u001b", "\\e")}")
        assertEquals("\u001b[31mred\u001b[m", ansi)
    }

    @Test
    fun testIncrementalAnsiOptimization() {
        val builder = AnnotatedString.Builder("BoldRed")
        builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), 0, 7)
        builder.addStyle(SpanStyle(color = Color(0xFFF44336)), 4, 7)
        val annotated = builder.toAnnotatedString()

        val ansi = AnsiParser.toAnsiString(annotated)
        assertEquals("\u001b[1mBold\u001b[31mRed\u001b[m", ansi)
    }

    @Test
    fun testUnsetOptimization() {
        val builder = AnnotatedString.Builder("BoldPlain")
        builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), 0, 4)
        val annotated = builder.toAnnotatedString()

        val ansi = AnsiParser.toAnsiString(annotated)
        // Bold is \u001b[1m
        // To go back to plain, \u001b[22m is 5 chars, \u001b[m is 4 chars.
        // Wait, \u001b[m is actually shorter.
        assertEquals("\u001b[1mBold\u001b[mPlain", ansi)
    }

    @Test
    fun testSwitchColorOptimization() {
        val builder = AnnotatedString.Builder("RedBlue")
        builder.addStyle(SpanStyle(color = Color(0xFFF44336)), 0, 3)
        builder.addStyle(SpanStyle(color = Color(0xFF2196F3)), 3, 7)
        val annotated = builder.toAnnotatedString()

        val ansi = AnsiParser.toAnsiString(annotated)
        // Red is 31, Blue is 34.
        // Incremental: \u001b[34m (5 chars)
        // Reset: \u001b[0;34m (7 chars)
        assertEquals("\u001b[31mRed\u001b[34mBlue\u001b[m", ansi)
    }
}
