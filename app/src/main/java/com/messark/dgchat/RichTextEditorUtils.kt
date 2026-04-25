package com.messark.dgchat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

object RichTextEditorUtils {

    fun toggleStyle(
        current: AnnotatedString,
        start: Int,
        end: Int,
        styleToToggle: SpanStyle
    ): AnnotatedString {
        if (start == end) return current
        val rangeStart = minOf(start, end)
        val rangeEnd = maxOf(start, end)

        // Check if the style is present at the start of the selection
        val isPresent = current.spanStyles.any {
            it.start <= rangeStart && it.end > rangeStart && it.item.contains(styleToToggle)
        }

        val builder = AnnotatedString.Builder(current.text)

        // We will reconstruct all spans
        val existingSpans = current.spanStyles

        // 1. Remove overlapping parts of the styleToToggle (or everything if we are adding and it's a color)
        val newSpans = mutableListOf<AnnotatedString.Range<SpanStyle>>()

        existingSpans.forEach { span ->
            if (span.end <= rangeStart || span.start >= rangeEnd) {
                // No overlap
                newSpans.add(span)
            } else {
                // Overlap. Split it.
                if (span.start < rangeStart) {
                    newSpans.add(AnnotatedString.Range(span.item, span.start, rangeStart))
                }

                val intersectStart = maxOf(span.start, rangeStart)
                val intersectEnd = minOf(span.end, rangeEnd)

                val newItem = if (span.item.contains(styleToToggle)) {
                    // Remove styleToToggle from this item
                    span.item.remove(styleToToggle)
                } else {
                    span.item
                }

                if (newItem != SpanStyle()) {
                    newSpans.add(AnnotatedString.Range(newItem, intersectStart, intersectEnd))
                }

                if (span.end > rangeEnd) {
                    newSpans.add(AnnotatedString.Range(span.item, rangeEnd, span.end))
                }
            }
        }

        // 2. If we are adding, add the styleToToggle to the range
        if (!isPresent) {
            newSpans.add(AnnotatedString.Range(styleToToggle, rangeStart, rangeEnd))
        }

        // 3. Add all new spans to builder, merging where possible (simplification: just add)
        newSpans.forEach {
            builder.addStyle(it.item, it.start, it.end)
        }

        return builder.toAnnotatedString()
    }

    private fun SpanStyle.contains(other: SpanStyle): Boolean {
        if (other.fontWeight != null && this.fontWeight == other.fontWeight) return true
        if (other.fontStyle != null && this.fontStyle == other.fontStyle) return true
        if (other.textDecoration != null && this.textDecoration == other.textDecoration) return true
        if (other.color != Color.Unspecified && this.color == other.color) return true
        return false
    }

    private fun SpanStyle.remove(other: SpanStyle): SpanStyle {
        var res = this
        if (other.fontWeight != null && this.fontWeight == other.fontWeight) res = res.copy(fontWeight = null)
        if (other.fontStyle != null && this.fontStyle == other.fontStyle) res = res.copy(fontStyle = null)
        if (other.textDecoration != null && this.textDecoration == other.textDecoration) res = res.copy(textDecoration = null)
        if (other.color != Color.Unspecified && this.color == other.color) res = res.copy(color = Color.Unspecified)
        return res
    }
}
