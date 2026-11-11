package com.puretv.mobilehelper.data

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendlyErrorSafetyTest {
    @Test
    fun backendStackTraceIsNotShownEvenWhenItContainsChinese() {
        val backend = "读取失败：java.lang.NullPointerException at com.example.Input.sendText(Input.java:42)"
        val error = IllegalStateException(backend).toFriendlyError()
        val displayed = listOfNotNull(error.title, error.hint).joinToString("\n")
        assertFalse(displayed.contains(backend))
        assertFalse(displayed.contains("NullPointerException"))
        assertFalse(displayed.contains("Input.java"))
    }

    @Test
    fun connectionErrorsDoNotExposeDeviceFilePaths() {
        val path = "/data/local/tmp/example-private-file"
        val error = IOException("failed to read $path").toFriendlyError()
        assertFalse(listOfNotNull(error.title, error.hint).joinToString("\n").contains(path))
    }

    @Test
    fun unknownInstallFailureKeepsItsCodeWithoutTheBackendTrace() {
        val code = "INSTALL_FAILED_EXAMPLE_REASON"
        val error = IOException("Failure [$code]\n at com.example.Installer.run(Installer.java:8)")
            .toInstallFailure()
        val displayed = listOfNotNull(error.title, error.hint).joinToString("\n")
        assertTrue(displayed.contains(code))
        assertFalse(displayed.contains("Installer.java"))
        assertFalse(displayed.contains("com.example"))
    }
}
