package com.puretv.mobilehelper.data

import java.io.IOException

internal class UnsupportedAdbInputTextException : IllegalArgumentException()
internal class RemoteInputCommandException : IOException()

/** Validate the insertion before the caller can issue any DEL events on the TV. */
internal fun validatedRemoteTextPrefix(sent: String, target: String): Int {
    var prefix = 0
    while (prefix < sent.length && prefix < target.length && sent[prefix] == target[prefix]) prefix++
    requireAdbInputText(target, prefix)
    return prefix
}

/** A portable subset for Android's `input text`, not an IME-based Unicode input path. */
internal fun requireAdbInputText(text: String, startIndex: Int = 0) {
    for (index in startIndex until text.length) {
        if (text[index] !in ' '..'~') throw UnsupportedAdbInputTextException()
    }
}
