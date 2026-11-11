package com.puretv.mobilehelper.data

internal class FocusedInputHintException : IllegalStateException()
internal class FocusedInputMissingException : IllegalStateException()
internal class FocusedInputMetadataException : IllegalStateException()

/** UIAutomator may expose an EditText's hint as text, without a showing-hint flag. */
internal object FocusedInputText {
    private val nodePattern = Regex("""<node\b[^>]*>""")
    private val focusedPattern = Regex("""\sfocused="true"""")
    private val attributePattern = Regex("""([\w-]+)="([^"]*)""")
    private val editorPattern = Regex(
        """(?m)^[ \t]*hintText=([\s\S]*?) label=[^\r\n]*\r?\n[ \t]*packageName=(\S+)"""
    )
    private val editableClasses = setOf(
        "android.widget.EditText",
        "android.widget.AutoCompleteTextView",
        "android.widget.MultiAutoCompleteTextView",
    )

    fun parse(xml: String, inputMethodDump: String): String? {
        for (node in nodePattern.findAll(xml)) {
            if (!focusedPattern.containsMatchIn(node.value)) continue
            val attributes = attributePattern.findAll(node.value).associate {
                it.groupValues[1] to decodeXml(it.groupValues[2])
            }
            if (attributes["class"] !in editableClasses && attributes["editable"] != "true") continue
            val text = attributes["text"] ?: return null
            if (text.isEmpty()) return text
            val editor = editorPattern.find(inputMethodDump) ?: throw FocusedInputMetadataException()
            if (editor.groupValues[2] != attributes["package"]) throw FocusedInputMetadataException()
            // Equal text is ambiguous: never assume the field is empty or adopt the hint as a baseline.
            if (text == editor.groupValues[1]) throw FocusedInputHintException()
            return text
        }
        return null
    }

    private fun decodeXml(value: String): String = value
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
}
