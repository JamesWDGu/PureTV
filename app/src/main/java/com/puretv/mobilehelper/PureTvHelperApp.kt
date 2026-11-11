package com.puretv.mobilehelper

import android.app.Application
import com.flyfishxu.kadb.cert.KadbCert
import com.puretv.mobilehelper.data.FilePrivateKeyStore
import java.io.File

class PureTvHelperApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Persist the host ADB identity so a TV only has to authorize this phone once.
        KadbCert.configure(
            store = FilePrivateKeyStore(File(filesDir, "adb/adbkey")),
        )
    }
}
