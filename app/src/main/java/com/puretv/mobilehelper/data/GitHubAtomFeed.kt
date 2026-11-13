package com.puretv.mobilehelper.data

/** One release parsed from GitHub's Atom feed, which needs no API quota. */
internal data class AtomRelease(
    val tag: String,
    val version: String,
    val notes: String,
)

internal fun atomUrl(repo: String): String = "https://github.com/$repo/releases.atom"

/** Parses the newest entry of a GitHub releases Atom feed. */
internal fun parseLatestAtomRelease(xml: String): AtomRelease? {
    val entry = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
        .find(xml)?.groupValues?.get(1) ?: return null
    val tag = Regex("/releases/tag/([^\"]+)").find(entry)?.groupValues?.get(1) ?: return null
    val version = parseAppVersion(tag)?.name ?: return null
    val encodedNotes = Regex("<content[^>]*>(.*?)</content>", RegexOption.DOT_MATCHES_ALL)
        .find(entry)?.groupValues?.get(1).orEmpty()
    return AtomRelease(tag = tag, version = version, notes = htmlToText(encodedNotes))
}

private fun htmlToText(html: String): String = html
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace(Regex("&#(x?[0-9a-fA-F]+);")) { match ->
        val raw = match.groupValues[1]
        val code = if (raw.startsWith("x", ignoreCase = true)) {
            raw.drop(1).toIntOrNull(16)
        } else {
            raw.toIntOrNull()
        }
        code?.takeIf { it in 1..0x10FFFF }
            ?.let { String(Character.toChars(it)) }
            ?: match.value
    }
    .replace(Regex("(?i)<br\\s*/?>"), "\n")
    .replace(Regex("(?i)</(p|h[1-6]|li|ul|ol)>"), "\n")
    .replace(Regex("<[^>]+>"), "")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace(Regex("[ \\t]+"), " ")
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()
