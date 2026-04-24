package com.messark.dgchat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

object AnsiParser {

    private val basicColors = listOf(
        Color(0xFF212121), // Black (Grey 900)
        Color(0xFFF44336), // Red (Red 500)
        Color(0xFF4CAF50), // Green (Green 500)
        Color(0xFFFFEB3B), // Yellow (Yellow 500)
        Color(0xFF2196F3), // Blue (Blue 500)
        Color(0xFF9C27B0), // Magenta (Purple 500)
        Color(0xFF00BCD4), // Cyan (Cyan 500)
        Color(0xFFEEEEEE)  // White (Grey 200)
    )

    private val brightColors = listOf(
        Color(0xFF9E9E9E), // Bright Black (Grey 500)
        Color(0xFFFF5252), // Bright Red (Red A200)
        Color(0xFF69F0AE), // Bright Green (Green A200)
        Color(0xFFFFFF00), // Bright Yellow (Yellow A200)
        Color(0xFF448AFF), // Bright Blue (Blue A200)
        Color(0xFFE040FB), // Bright Magenta (Purple A200)
        Color(0xFF18FFFF), // Bright Cyan (Cyan A200)
        Color(0xFFFFFFFF)  // Bright White
    )

    fun parseAnsi(text: String): AnnotatedString {
        val builder = AnnotatedString.Builder()
        val regex = Regex("\u001b\\[([\\d;]*)m")
        var lastMatchEnd = 0

        var currentSpanStyle = SpanStyle()

        regex.findAll(text).forEach { match ->
            // Append text before the match
            val textBefore = text.substring(lastMatchEnd, match.range.first)
            if (textBefore.isNotEmpty()) {
                if (currentSpanStyle != SpanStyle()) {
                    builder.pushStyle(currentSpanStyle)
                    builder.append(textBefore)
                    builder.pop()
                } else {
                    builder.append(textBefore)
                }
            }

            val params = match.groupValues[1].split(';').filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
            if (params.isEmpty()) {
                // Treat empty as reset
                currentSpanStyle = SpanStyle()
            } else {
                var i = 0
                while (i < params.size) {
                    val code = params[i]
                    when (code) {
                        0 -> currentSpanStyle = SpanStyle() // Reset
                        1 -> currentSpanStyle = currentSpanStyle.copy(fontWeight = FontWeight.Bold)
                        3 -> currentSpanStyle = currentSpanStyle.copy(fontStyle = FontStyle.Italic)
                        4 -> currentSpanStyle = currentSpanStyle.copy(textDecoration = TextDecoration.Underline)
                        in 30..37 -> currentSpanStyle = currentSpanStyle.copy(color = basicColors[code - 30])
                        38 -> { // Extended foreground
                            val (color, consumed) = parseExtendedColor(params, i + 1)
                            if (color != null) currentSpanStyle = currentSpanStyle.copy(color = color)
                            i += consumed
                        }
                        39 -> currentSpanStyle = currentSpanStyle.copy(color = Color.Unspecified) // Default fg
                        in 40..47 -> currentSpanStyle = currentSpanStyle.copy(background = basicColors[code - 40])
                        48 -> { // Extended background
                            val (color, consumed) = parseExtendedColor(params, i + 1)
                            if (color != null) currentSpanStyle = currentSpanStyle.copy(background = color)
                            i += consumed
                        }
                        49 -> currentSpanStyle = currentSpanStyle.copy(background = Color.Unspecified) // Default bg
                        in 90..97 -> currentSpanStyle = currentSpanStyle.copy(color = brightColors[code - 90])
                        in 100..107 -> currentSpanStyle = currentSpanStyle.copy(background = brightColors[code - 100])
                    }
                    i++
                }
            }

            lastMatchEnd = match.range.last + 1
        }

        // Append remaining text
        val remainingText = text.substring(lastMatchEnd)
        if (remainingText.isNotEmpty()) {
            if (currentSpanStyle != SpanStyle()) {
                builder.pushStyle(currentSpanStyle)
                builder.append(remainingText)
                builder.pop()
            } else {
                builder.append(remainingText)
            }
        }

        return builder.toAnnotatedString()
    }

    private fun parseExtendedColor(params: List<Int>, startIndex: Int): Pair<Color?, Int> {
        if (startIndex >= params.size) return null to 0
        return when (params[startIndex]) {
            5 -> { // 256 colors
                if (startIndex + 1 < params.size) {
                    val colorIndex = params[startIndex + 1]
                    color256(colorIndex) to 2
                } else null to 1
            }
            2 -> { // RGB
                if (startIndex + 3 < params.size) {
                    Color(params[startIndex + 1], params[startIndex + 2], params[startIndex + 3]) to 4
                } else null to 1
            }
            else -> null to 1
        }
    }

    private fun color256(index: Int): Color {
        return when (index) {
            in 0..7 -> basicColors[index]
            in 8..15 -> brightColors[index - 8]
            in 16..231 -> {
                val r = (index - 16) / 36
                val g = ((index - 16) % 36) / 6
                val b = (index - 16) % 6
                Color(
                    red = (if (r == 0) 0 else r * 40 + 55) / 255f,
                    green = (if (g == 0) 0 else g * 40 + 55) / 255f,
                    blue = (if (b == 0) 0 else b * 40 + 55) / 255f
                )
            }
            in 232..255 -> {
                val gray = ((index - 232) * 10 + 8) / 255f
                Color(gray, gray, gray)
            }
            else -> Color.Unspecified
        }
    }
}
