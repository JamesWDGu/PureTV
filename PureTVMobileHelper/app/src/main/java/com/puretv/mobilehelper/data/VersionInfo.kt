package com.puretv.mobilehelper.data

/** MAJOR.MINOR.PATCH; versionCode is derived so every release increases it. */
internal data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<AppVersion> {
    val code: Int get() = major * 10_000 + minor * 100 + patch
    val name: String get() = "$major.$minor.$patch"

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
}

/** Accepts "1.2.3", "v1.2.3", and "1.2" (treated as 1.2.0). */
internal fun parseAppVersion(raw: String?): AppVersion? {
    val cleaned = raw?.trim()?.removePrefix("v")?.removePrefix("V") ?: return null
    val numeric = cleaned.takeWhile { it.isDigit() || it == '.' }
    val parts = numeric.split('.').filter { it.isNotEmpty() }
    if (parts.size !in 2..3) return null
    val numbers = parts.map { it.toIntOrNull() ?: return null }
    if (numbers.any { it !in 0..9999 }) return null
    return AppVersion(numbers[0], numbers[1], numbers.getOrElse(2) { 0 })
}

internal fun isNewerVersion(remote: String?, current: String?): Boolean {
    val remoteVersion = parseAppVersion(remote) ?: return false
    val currentVersion = parseAppVersion(current) ?: return false
    return remoteVersion > currentVersion
}

/** Release bodies are Markdown; the dialog shows plain text. */
internal fun plainReleaseNotes(markdown: String): String {
    val output = ArrayList<String>()
    var previousBlank = false
    for (raw in markdown.lineSequence()) {
        var line = raw.trim()
        if (line == "---" || line == "***" || line == "___") continue
        line = line.replace(Regex("""^#{1,6}\s+"""), "")
        line = line.replace("**", "").replace("__", "").replace("`", "")
        val blank = line.isEmpty()
        if (blank && (previousBlank || output.isEmpty())) continue
        output.add(line)
        previousBlank = blank
    }
    while (output.isNotEmpty() && output.last().isEmpty()) output.removeAt(output.lastIndex)
    return output.joinToString("\n")
}
