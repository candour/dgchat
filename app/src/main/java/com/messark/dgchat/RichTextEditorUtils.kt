package com.messark.dgchat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration

object RichTextEditorUtils {
    fun updateAnnotatedString(
        oldValue: TextFieldValue,
        newValue: TextFieldValue,
        activeStyles: Set<SpanStyle>
    ): AnnotatedString {
        val oldText = oldValue.text
        val newText = newValue.text
        val oldSelection = oldValue.selection

        if (oldText == newText) {
            // Even if text didn't change, we might want to return the old annotated string
            // to ensure we don't lose spans if newValue.annotatedString is plain.
            return oldValue.annotatedString
        }

        val changeStart = minOf(oldSelection.start, oldSelection.end)
        val changeEnd = maxOf(oldSelection.start, oldSelection.end)
        val removedLength = changeEnd - changeStart
        val addedLength = newText.length - (oldText.length - removedLength)
        val diff = addedLength - removedLength

        val builder = AnnotatedString.Builder(newText)

        // Process old spans
        oldValue.annotatedString.spanStyles.forEach { span ->
            val oldStart = span.start
            val oldEnd = span.end
            val style = span.item

            // Split and shift
            if (oldStart < changeStart) {
                val end = minOf(oldEnd, changeStart)
                if (oldStart < end) {
                    builder.addStyle(style, oldStart, end)
                }
            }
            if (oldEnd > changeEnd) {
                val start = maxOf(oldStart, changeEnd) + diff
                val end = oldEnd + diff
                if (start < end) {
                    builder.addStyle(style, start, end)
                }
            }
        }

        // Apply active styles to the new text
        if (addedLength > 0) {
            activeStyles.forEach { style ->
                builder.addStyle(style, changeStart, changeStart + addedLength)
            }
        }

        return builder.toAnnotatedString()
    }
}
