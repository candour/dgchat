package com.messark.dgchat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

object AnsiParser {

    val basicColors = listOf(
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
                        22 -> currentSpanStyle = currentSpanStyle.copy(fontWeight = null)
                        3 -> currentSpanStyle = currentSpanStyle.copy(fontStyle = FontStyle.Italic)
                        23 -> currentSpanStyle = currentSpanStyle.copy(fontStyle = null)
                        4 -> currentSpanStyle = currentSpanStyle.copy(textDecoration = TextDecoration.Underline)
                        24 -> currentSpanStyle = currentSpanStyle.copy(textDecoration = null)
                        in 30..37 -> currentSpanStyle = currentSpanStyle.copy(color = basicColors[code - 30])
                        38 -> { // Extended foreground
                            val (color, consumed) = parseExtendedColor(params, i + 1)
                            if (color != null) currentSpanStyle = currentSpanStyle.copy(color = color)
                            i += consumed + 1
                            continue
                        }
                        39 -> currentSpanStyle = currentSpanStyle.copy(color = Color.Unspecified) // Default fg
                        in 40..47 -> currentSpanStyle = currentSpanStyle.copy(background = basicColors[code - 40])
                        48 -> { // Extended background
                            val (color, consumed) = parseExtendedColor(params, i + 1)
                            if (color != null) currentSpanStyle = currentSpanStyle.copy(background = color)
                            i += consumed + 1
                            continue
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

    fun toAnsiString(annotatedString: AnnotatedString): String {
        val sb = StringBuilder()
        val text = annotatedString.text
        val spanStyles = annotatedString.spanStyles

        val transitionPoints = (spanStyles.flatMap { listOf(it.start, it.end) } + 0 + text.length)
            .filter { it in 0..text.length }
            .distinct()
            .sorted()

        var currentState = AnsiState()

        for (i in 0 until transitionPoints.size - 1) {
            val start = transitionPoints[i]
            val end = transitionPoints[i + 1]
            if (start == end) continue

            var rangeStyle = SpanStyle()
            spanStyles.filter { it.start < end && it.end > start }.forEach {
                rangeStyle = rangeStyle.merge(it.item)
            }
            val targetState = rangeStyle.toAnsiState()

            if (targetState != currentState) {
                sb.append(getBestTransition(currentState, targetState))
                currentState = targetState
            }
            sb.append(text.substring(start, end))
        }

        if (currentState != AnsiState()) {
            sb.append(getBestTransition(currentState, AnsiState()))
        }

        return sb.toString()
    }

    private data class AnsiState(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val fgColor: Color = Color.Unspecified,
        val bgColor: Color = Color.Unspecified
    )

    private fun SpanStyle.toAnsiState(): AnsiState {
        return AnsiState(
            bold = fontWeight == FontWeight.Bold,
            italic = fontStyle == FontStyle.Italic,
            underline = textDecoration == TextDecoration.Underline,
            fgColor = color,
            bgColor = background
        )
    }

    private fun getColorCodes(color: Color, isForeground: Boolean): List<Int> {
        if (color == Color.Unspecified) return emptyList()
        val codes = mutableListOf<Int>()
        val base = if (isForeground) 30 else 40
        val brightBase = if (isForeground) 90 else 100

        val colorArgb = color.toArgb()
        val basicIndex = basicColors.indexOfFirst { it.toArgb() == colorArgb }
        if (basicIndex != -1) {
            codes.add(base + basicIndex)
        } else {
            val brightIndex = brightColors.indexOfFirst { it.toArgb() == colorArgb }
            if (brightIndex != -1) {
                codes.add(brightBase + brightIndex)
            } else {
                codes.add(base + 8)
                codes.add(2)
                codes.add((color.red * 255).toInt())
                codes.add((color.green * 255).toInt())
                codes.add((color.blue * 255).toInt())
            }
        }
        return codes
    }

    private fun getBestTransition(current: AnsiState, target: AnsiState): String {
        if (current == target) return ""

        val incrementalCodes = mutableListOf<Int>()
        if (target.bold != current.bold) incrementalCodes.add(if (target.bold) 1 else 22)
        if (target.italic != current.italic) incrementalCodes.add(if (target.italic) 3 else 23)
        if (target.underline != current.underline) incrementalCodes.add(if (target.underline) 4 else 24)

        if (target.fgColor != current.fgColor) {
            if (target.fgColor == Color.Unspecified) incrementalCodes.add(39)
            else incrementalCodes.addAll(getColorCodes(target.fgColor, true))
        }

        if (target.bgColor != current.bgColor) {
            if (target.bgColor == Color.Unspecified) incrementalCodes.add(49)
            else incrementalCodes.addAll(getColorCodes(target.bgColor, false))
        }

        // FIX START: If no codes were added, return empty string immediately
        if (incrementalCodes.isEmpty() && target != AnsiState()) return ""
        
        val incrementalStr = "\u001b[${incrementalCodes.joinToString(";")}m"
        // FIX END

        // Reset option
        val resetCodes = mutableListOf<Int>()
        resetCodes.add(0)
        if (target.bold) resetCodes.add(1)
        if (target.italic) resetCodes.add(3)
        if (target.underline) resetCodes.add(4)
        if (target.fgColor != Color.Unspecified) resetCodes.addAll(getColorCodes(target.fgColor, true))
        if (target.bgColor != Color.Unspecified) resetCodes.addAll(getColorCodes(target.bgColor, false))

        val resetStr = if (target == AnsiState()) "\u001b[0m" // Explicit 0 is safer
        else "\u001b[${resetCodes.joinToString(";")}m"

        return if (incrementalStr.length <= resetStr.length) incrementalStr else resetStr
}

