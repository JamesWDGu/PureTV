package com.puretv.mobilehelper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoreInstallerTest {

    @Test
    fun prefersStoreNamedAsset() {
        val json = """
            {"tag_name":"v1.2.3","body":"notes","assets":[
              {"name":"other.apk","browser_download_url":"https://example.com/other.apk","size":1},
              {"name":"PureTVStore-1.2.3.apk","browser_download_url":"https://example.com/store.apk","size":42}
            ]}
        """.trimIndent()

        val release = parseStoreRelease(json)

        assertEquals("1.2.3", release?.versionName)
        assertEquals("https://example.com/store.apk", release?.apkUrl)
        assertEquals(42L, release?.apkSize)
    }

    @Test
    fun fallsBackToFirstApkWhenStoreAssetMissing() {
        val json = """
            {"tag_name":"2.0.0","assets":[
              {"name":"notes.txt","browser_download_url":"https://example.com/notes.txt","size":1},
              {"name":"app.apk","browser_download_url":"https://example.com/app.apk","size":7}
            ]}
        """.trimIndent()

        val release = parseStoreRelease(json)

        assertEquals("2.0.0", release?.versionName)
        assertEquals("https://example.com/app.apk", release?.apkUrl)
    }

    @Test
    fun returnsNullWhenNoDownloadableApk() {
        val json = """{"tag_name":"v3.0.0","assets":[{"name":"notes.txt","browser_download_url":""}]}"""
        assertNull(parseStoreRelease(json))
    }

    @Test
    fun atomFallbackBuildsStandardStoreAssetUrl() {
        val xml = """
            <feed>
              <entry>
                <link rel="alternate" type="text/html"
                  href="https://github.com/JamesWDGu/PureTVStore/releases/tag/v1.2.2"/>
                <title>PureTV 商店 1.2.2</title>
                <content type="html">&lt;h3&gt;修复&lt;/h3&gt;&lt;p&gt;版本 1.2.2&lt;/p&gt;</content>
              </entry>
            </feed>
        """.trimIndent()

        val release = storeReleaseFromAtom(xml)

        assertEquals("1.2.2", release?.versionName)
        assertEquals(
            "https://github.com/JamesWDGu/PureTVStore/releases/download/v1.2.2/" +
                "PureTVStore-1.2.2.apk",
            release?.apkUrl,
        )
        assertEquals(true, release?.notes?.contains("版本 1.2.2"))
    }
}
