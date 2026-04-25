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

class RichTextEditorUtilsTest {

    @Test
    fun testToggleBold() {
        val text = AnnotatedString("Hello World")
        val boldStyle = SpanStyle(fontWeight = FontWeight.Bold)

        // Apply bold to "Hello"
        val styled = RichTextEditorUtils.toggleStyle(text, 0, 5, boldStyle)
        assertEquals("Hello World", styled.text)
        assertEquals(1, styled.spanStyles.size)
        assertEquals(FontWeight.Bold, styled.spanStyles[0].item.fontWeight)
        assertEquals(0, styled.spanStyles[0].start)
        assertEquals(5, styled.spanStyles[0].end)

        // Toggle bold off for "Hello"
        val unstyled = RichTextEditorUtils.toggleStyle(styled, 0, 5, boldStyle)
        assertEquals(0, unstyled.spanStyles.size)
    }

    @Test
    fun testToggleColor() {
        val text = AnnotatedString("Color Me")
        val redStyle = SpanStyle(color = Color.Red)

        val styled = RichTextEditorUtils.toggleStyle(text, 0, 5, redStyle)
        assertEquals(Color.Red, styled.spanStyles[0].item.color)

        val unstyled = RichTextEditorUtils.toggleStyle(styled, 0, 5, redStyle)
        assertTrue(unstyled.spanStyles.isEmpty())
    }
}
