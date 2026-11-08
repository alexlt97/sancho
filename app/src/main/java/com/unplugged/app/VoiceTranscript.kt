package com.unplugged.app

import java.util.Locale

internal fun firstRecognizedPhrase(results: List<String>?): String? = results
    .orEmpty()
    .asSequence()
    .map(String::trim)
    .firstOrNull(String::isNotEmpty)

internal fun isVoiceStopCommand(phrase: String): Boolean {
    val normalized = phrase
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
    return normalized in setOf(
        "stop",
        "cancel",
        "stop sancho",
        "cancel sancho",
        "never mind",
        "be quiet",
    )
}
