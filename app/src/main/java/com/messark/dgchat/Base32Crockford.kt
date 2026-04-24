package com.messark.dgchat

object Base32Crockford {
    private const val ALPHABET = "0123456789abcdefghjkmnpqrstvwxyz"
    private val DECODE_MAP = IntArray(256) { -1 }

    init {
        for (i in ALPHABET.indices) {
            DECODE_MAP[ALPHABET[i].code] = i
        }
        // Crockford maps I, L to 1 and O to 0
        DECODE_MAP['I'.code] = 1
        DECODE_MAP['i'.code] = 1
        DECODE_MAP['L'.code] = 1
        DECODE_MAP['l'.code] = 1
        DECODE_MAP['O'.code] = 0
        DECODE_MAP['o'.code] = 0
    }

    fun encode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val result = StringBuilder()
        var buffer = 0L
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toLong() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                val index = ((buffer shr bits) and 0x1F).toInt()
                result.append(ALPHABET[index])
            }
        }
        if (bits > 0) {
            val index = ((buffer shl (5 - bits)) and 0x1F).toInt()
            result.append(ALPHABET[index])
        }
        return result.toString()
    }

    fun decode(s: String): ByteArray {
        val cleanS = s.lowercase().replace("-", "")
        if (cleanS.isEmpty()) return byteArrayOf()

        val result = mutableListOf<Byte>()
        var buffer = 0L
        var bits = 0
        for (c in cleanS) {
            val value = DECODE_MAP[c.code]
            if (value == -1) continue
            buffer = (buffer shl 5) or value.toLong()
            bits += 5
            if (bits >= 8) {
                bits -= 8
                result.add(((buffer shr bits) and 0xFF).toByte())
            }
        }
        return result.toByteArray()
    }
}
