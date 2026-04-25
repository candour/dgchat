package com.messark.dgchat

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class RichTextEditorUtilsTest {

    private val boldStyle = SpanStyle(fontWeight = FontWeight.Bold)

    @Test
    fun testAppendTextWithStyle() {
        val oldValue = TextFieldValue(AnnotatedString(""), selection = TextRange(0))
        val newValue = TextFieldValue(AnnotatedString("H"), selection = TextRange(1))
        val activeStyles = setOf(boldStyle)

        val result = RichTextEditorUtils.updateAnnotatedString(oldValue, newValue, activeStyles)

        assertEquals("H", result.text)
        assertEquals(1, result.spanStyles.size)
        assertEquals(0, result.spanStyles[0].start)
        assertEquals(1, result.spanStyles[0].end)
        assertEquals(boldStyle, result.spanStyles[0].item)
    }

    @Test
    fun testPreserveExistingStyleOnAppend() {
        val builder = AnnotatedString.Builder("H")
        builder.addStyle(boldStyle, 0, 1)
        val oldValue = TextFieldValue(builder.toAnnotatedString(), selection = TextRange(1))
        val newValue = TextFieldValue(AnnotatedString("He"), selection = TextRange(2))
        val activeStyles = setOf(boldStyle)

        val result = RichTextEditorUtils.updateAnnotatedString(oldValue, newValue, activeStyles)

        assertEquals("He", result.text)
        // Should have one span from 0 to 2 (merged if contiguous and same style,
        // but our implementation adds them separately, which is also fine for rendering)
        // Actually our implementation will have two spans: [0, 1] and [1, 2]
        assertEquals(2, result.spanStyles.size)

        val sortedSpans = result.spanStyles.sortedBy { it.start }
        assertEquals(0, sortedSpans[0].start)
        assertEquals(1, sortedSpans[0].end)
        assertEquals(1, sortedSpans[1].start)
        assertEquals(2, sortedSpans[1].end)
    }

    @Test
    fun testInsertInMiddleWithDifferentStyle() {
        val builder = AnnotatedString.Builder("Hello")
        builder.addStyle(boldStyle, 0, 5)
        val oldValue = TextFieldValue(builder.toAnnotatedString(), selection = TextRange(2))
        val newValue = TextFieldValue(AnnotatedString("Hexllo"), selection = TextRange(3))
        val activeStyles = emptySet<SpanStyle>()

        val result = RichTextEditorUtils.updateAnnotatedString(oldValue, newValue, activeStyles)

        assertEquals("Hexllo", result.text)
        // Spans should be: [0, 2] and [3, 6]
        assertEquals(2, result.spanStyles.size)
        val sortedSpans = result.spanStyles.sortedBy { it.start }

        assertEquals(0, sortedSpans[0].start)
        assertEquals(2, sortedSpans[0].end)
        assertEquals(boldStyle, sortedSpans[0].item)

        assertEquals(3, sortedSpans[1].start)
        assertEquals(6, sortedSpans[1].end)
        assertEquals(boldStyle, sortedSpans[1].item)
    }

    @Test
    fun testDeleteInMiddle() {
        val builder = AnnotatedString.Builder("Hello")
        builder.addStyle(boldStyle, 0, 5)
        val oldValue = TextFieldValue(builder.toAnnotatedString(), selection = TextRange(2, 3)) // Select 'l'
        val newValue = TextFieldValue(AnnotatedString("Helo"), selection = TextRange(2))
        val activeStyles = setOf(boldStyle)

        val result = RichTextEditorUtils.updateAnnotatedString(oldValue, newValue, activeStyles)

        assertEquals("Helo", result.text)
        // Spans should be: [0, 2] and [2, 4]
        assertEquals(2, result.spanStyles.size)
        val sortedSpans = result.spanStyles.sortedBy { it.start }

        assertEquals(0, sortedSpans[0].start)
        assertEquals(2, sortedSpans[0].end)

        assertEquals(2, sortedSpans[1].start)
        assertEquals(4, sortedSpans[1].end)
    }

    @Test
    fun testBackwardSelectionDelete() {
        val builder = AnnotatedString.Builder("Hello")
        builder.addStyle(boldStyle, 0, 5)
        // Backward selection from 3 to 2
        val oldValue = TextFieldValue(builder.toAnnotatedString(), selection = TextRange(3, 2))
        val newValue = TextFieldValue(AnnotatedString("Helo"), selection = TextRange(2))
        val activeStyles = setOf(boldStyle)

        val result = RichTextEditorUtils.updateAnnotatedString(oldValue, newValue, activeStyles)

        assertEquals("Helo", result.text)
        assertEquals(2, result.spanStyles.size)
        val sortedSpans = result.spanStyles.sortedBy { it.start }

        assertEquals(0, sortedSpans[0].start)
        assertEquals(2, sortedSpans[0].end)

        assertEquals(2, sortedSpans[1].start)
        assertEquals(4, sortedSpans[1].end)
    }
}
