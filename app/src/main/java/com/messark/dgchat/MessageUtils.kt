package com.messark.dgchat

object MessageUtils {
    /**
     * Splits a string into multiple parts based on a byte limit.
     * Respects UTF-8 character boundaries and prefers splitting at word boundaries (spaces).
     * Also avoids splitting in the middle of ANSI escape sequences.
     */
    fun splitMessage(text: String, byteLimit: Int): List<String> {
        if (text.isEmpty()) return emptyList()
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size <= byteLimit) return listOf(text)

        val result = mutableListOf<String>()
        var currentStart = 0

        while (currentStart < text.length) {
            var bestEnd = currentStart
            var currentByteCount = 0

            var i = currentStart
            while (i < text.length) {
                val codePoint = text.codePointAt(i)
                val charCount = Character.charCount(codePoint)
                val charStr = text.substring(i, i + charCount)
                val charBytes = charStr.toByteArray(Charsets.UTF_8)

                if (currentByteCount + charBytes.size > byteLimit) {
                    break
                }

                // Avoid splitting inside ANSI escape sequence
                if (text[i] == '\u001b') {
                    // Peek ahead for 'm'
                    val mIndex = text.indexOf('m', i)
                    if (mIndex != -1) {
                        val sequence = text.substring(i, mIndex + 1)
                        val sequenceBytes = sequence.toByteArray(Charsets.UTF_8)
                        if (currentByteCount + sequenceBytes.size <= byteLimit) {
                            currentByteCount += sequenceBytes.size
                            i = mIndex + 1
                            bestEnd = i
                            continue
                        } else {
                            // Sequence doesn't fit, must split before it
                            break
                        }
                    }
                }

                currentByteCount += charBytes.size
                i += charCount
                bestEnd = i
            }

            if (bestEnd == currentStart) {
                // Could not even fit one character or ANSI sequence?
                // This might happen if a single character's UTF-8 representation exceeds byteLimit.
                // In that case, we MUST take it to avoid infinite loop, even if it exceeds limit.
                val codePoint = text.codePointAt(currentStart)
                bestEnd = currentStart + Character.charCount(codePoint)
            } else if (bestEnd < text.length) {
                // Try to find the last space to split at word boundary
                val lastSpace = text.substring(currentStart, bestEnd).lastIndexOf(' ')
                if (lastSpace != -1) {
                    bestEnd = currentStart + lastSpace + 1 // Include the space at the end of the current part
                }
            }

            result.add(text.substring(currentStart, bestEnd))
            currentStart = bestEnd
        }

        return result
    }

    fun calculateLimit(baseDomain: String): Int {
        return 94 - baseDomain.length
    }
}
