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
}
