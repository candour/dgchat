package com.messark.dgchat

object NicknameUtils {
    fun isValid(nickname: String): Boolean {
        if (nickname.length > 32 || nickname.isEmpty()) return false
        val regex = Regex("^[\\p{L}0-9-]+$")
        return regex.matches(nickname)
    }

    fun generateDefaultNickname(): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        val randomString = (1..8)
            .map { allowedChars.random() }
            .joinToString("")
        return "androiduser$randomString"
    }

    fun getNicknameColorIndex(nickname: String, paletteSize: Int): Int {
        if (paletteSize <= 0) return 0
        val hash = nickname.hashCode()
        return (hash and 0x7FFFFFFF) % paletteSize
    }
}
