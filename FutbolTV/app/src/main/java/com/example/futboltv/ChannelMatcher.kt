package com.example.futboltv

fun matchStream(eventChannel: String, streams: List<Stream>): Stream? {
    val normalized = eventChannel.lowercase()

    return streams.firstOrNull { s ->
        val name = s.name.lowercase()
        name.contains(normalized.take(5)) ||
                normalized.contains(name.take(5))
    }
}
