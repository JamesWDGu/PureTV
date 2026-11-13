package com.puretv.mobilehelper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {

    private val feed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <entry>
            <link rel="alternate" type="text/html"
              href="https://github.com/JamesWDGu/PureTVMobileHelper/releases/tag/v1.2.0"/>
            <title>PureTV 1.2.0</title>
            <content type="html">&lt;h3&gt;新增&lt;/h3&gt;
        &lt;ul&gt;&lt;li&gt;安装 PureTV 商店&lt;/li&gt;&lt;/ul&gt;
        &lt;p&gt;版本 1.2.0（10200）&lt;/p&gt;</content>
          </entry>
        </feed>
    """.trimIndent()

    @Test
    fun `parses latest release from atom feed`() {
        val update = parseAtomRelease(feed)!!
        assertEquals("1.2.0", update.versionName)
        assertEquals(
            "https://github.com/JamesWDGu/PureTVMobileHelper/releases/download/v1.2.0/PureTV-1.2.0.apk",
            update.apkUrl,
        )
        assertTrue(update.notes.contains("安装 PureTV 商店"))
        assertTrue(update.notes.contains("版本 1.2.0"))
        assertTrue(!update.notes.contains("<"))
    }

    @Test
    fun `ignores feed without entries`() {
        assertNull(parseAtomRelease("<feed></feed>"))
    }
}
