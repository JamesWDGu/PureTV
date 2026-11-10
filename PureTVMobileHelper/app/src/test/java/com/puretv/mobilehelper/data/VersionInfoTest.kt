package com.puretv.mobilehelper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionInfoTest {
    @Test
    fun parsesTagWithPrefixAndTwoSegments() {
        assertEquals(AppVersion(1, 2, 3), parseAppVersion("v1.2.3"))
        assertEquals(AppVersion(1, 2, 0), parseAppVersion("1.2"))
    }

    @Test
    fun rejectsInvalidVersions() {
        assertNull(parseAppVersion(null))
        assertNull(parseAppVersion(""))
        assertNull(parseAppVersion("v"))
        assertNull(parseAppVersion("1"))
        assertNull(parseAppVersion("1.x.0"))
    }

    @Test
    fun comparesNumericallyNotLexically() {
        assertTrue(isNewerVersion("1.10.0", "1.9.9"))
        assertFalse(isNewerVersion("1.9.9", "1.10.0"))
        assertFalse(isNewerVersion("2.0.0", "2.0.0"))
    }

    @Test
    fun refusesToPromptWhenVersionsAreNotComparable() {
        assertFalse(isNewerVersion("bad", "1.0.0"))
        assertFalse(isNewerVersion("1.0.1", "bad"))
    }

    @Test
    fun versionCodeIsMonotonicAndMatchesBuildRule() {
        assertEquals(10_100, parseAppVersion("1.1.0")?.code)
        assertEquals(11_000, parseAppVersion("1.10.0")?.code)
        assertTrue(parseAppVersion("1.10.0")!!.code > parseAppVersion("1.9.9")!!.code)
        assertTrue(parseAppVersion("2.0.0")!!.code > parseAppVersion("1.99.99")!!.code)
    }

    @Test
    fun normalizesNameWithoutPrefix() {
        assertEquals("1.1.0", parseAppVersion("v1.1.0")?.name)
    }
}
