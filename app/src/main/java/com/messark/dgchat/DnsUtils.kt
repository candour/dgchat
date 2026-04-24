package com.messark.dgchat

fun makeDnsLabels(s: String): String {
    val labels = mutableListOf<String>()
    var remaining = s
    while (remaining.length > 63) {
        labels.add(remaining.substring(0, 63))
        remaining = remaining.substring(63)
    }
    labels.add(remaining)
    return labels.joinToString(".")
}
