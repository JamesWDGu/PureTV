package com.puretv.mobilehelper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FocusedInputTextTest {
    @Test
    fun hintOrLiteralTextIdenticalToHintIsNotAdopted() {
        assertThrows(FocusedInputHintException::class.java) {
            FocusedInputText.parse(field("Search apps"), editor("Search apps"))
        }
    }

    @Test
    fun realTextIsPreservedWhenDifferentFromHint() {
        assertEquals("hello world", FocusedInputText.parse(field("hello world"), editor("Search apps")))
    }

    @Test
    fun xmlEscapesAreDecodedWithoutChangingLiteralEntities() {
        assertEquals("a & <b> \"c\" &amp;", FocusedInputText.parse(
            field("a &amp; &lt;b&gt; &quot;c&quot; &amp;amp;"), editor("Search apps")
        ))
    }

    @Test
    fun emptyFieldIsValidWithoutEditorMetadata() {
        assertEquals("", FocusedInputText.parse(field(""), ""))
    }

    @Test
    fun focusedLabelsAreNotInputFields() {
        assertNull(FocusedInputText.parse(field("Search apps", className = "android.widget.TextView"), ""))
    }

    @Test
    fun editableFieldMayContainChildAccessibilityNodes() {
        val xml = field("hello").replace("/>", "><node text=\"child\" focused=\"false\" /></node>")
        assertEquals("hello", FocusedInputText.parse(xml, editor("Search apps")))
    }

    @Test
    fun staleMetadataFromAnotherPackageCannotVerifyText() {
        val metadata = editor("Search apps", packageName = "com.example.other") + editor("Search apps")
        assertThrows(FocusedInputMetadataException::class.java) {
            FocusedInputText.parse(field("hello"), metadata)
        }
    }

    @Test
    fun nonemptyTextWithoutHintMetadataIsNotAdopted() {
        assertThrows(FocusedInputMetadataException::class.java) {
            FocusedInputText.parse(field("Search apps"), "Permission denied")
        }
    }

    @Test
    fun unfocusedInputIsNotRead() {
        assertNull(FocusedInputText.parse(field("hello").replace("focused=\"true\"", "focused=\"false\""),
            editor("Search apps")))
    }

    private fun field(text: String, className: String = "android.widget.EditText"): String =
        """<hierarchy><node text="$text" focused="true" class="$className" package="com.example.tv" /></hierarchy>"""

    private fun editor(hint: String, packageName: String = "com.example.tv"): String = """
        mCurrentEditorInfo:
          inputType=0x1 imeOptions=0x0 privateImeOptions=null
          initialSelStart=0 initialSelEnd=0 initialCapsMode=0x0
          hintText=$hint label=null
          packageName=$packageName autofillId=1 fieldId=42 fieldName=null
    """.trimIndent() + "\n"
}
