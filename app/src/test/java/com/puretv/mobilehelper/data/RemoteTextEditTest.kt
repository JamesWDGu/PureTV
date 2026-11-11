package com.puretv.mobilehelper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoteTextEditTest {
    @Test
    fun unsupportedReplacementIsRejectedBeforeAnEditCanBeApplied() {
        assertThrows(UnsupportedAdbInputTextException::class.java) {
            validatedRemoteTextPrefix("existing TV text", "中文")
        }
    }

    @Test
    fun replacementPreservesTheCommonPrefix() {
        assertEquals(5, validatedRemoteTextPrefix("same old", "same new tail"))
    }

    @Test
    fun asciiCanBeAppendedToExistingUnicodeWithoutReplayingIt() {
        assertEquals(2, validatedRemoteTextPrefix("中文", "中文abc 123"))
    }

    @Test
    fun deletingExistingUnicodeDoesNotRequireTextInjection() {
        assertEquals(4, validatedRemoteTextPrefix("keep中文", "keep"))
    }

    @Test
    fun noChangeDoesNotRejectExistingUnicode() {
        assertEquals(2, validatedRemoteTextPrefix("中文", "中文"))
    }
}
