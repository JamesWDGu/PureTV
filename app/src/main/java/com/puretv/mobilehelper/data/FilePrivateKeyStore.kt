package com.puretv.mobilehelper.data

import com.flyfishxu.kadb.cert.KadbPrivateKeyStore
import java.io.File
import java.io.FileOutputStream

/**
 * Stores the ADB private key in app-private storage. Writes go through a temp file + fsync +
 * rename so a crash cannot leave a truncated key that would force a new TV authorization.
 */
class FilePrivateKeyStore(private val file: File) : KadbPrivateKeyStore {

    override fun readPrivateKeyPem(): ByteArray? {
        if (!file.exists()) return null
        return file.readBytes()
    }

    override fun writePrivateKeyPemAtomic(privateKeyPem: ByteArray) {
        val parent = file.parentFile ?: error("Invalid ADB key path: $file")
        if (!parent.exists() && !parent.mkdirs()) {
            error("Unable to create ADB key directory: $parent")
        }
        val temp = File(parent, "${file.name}.tmp")
        FileOutputStream(temp).use { output ->
            output.write(privateKeyPem)
            output.flush()
            output.fd.sync()
        }
        if (!temp.renameTo(file)) {
            // Same-directory rename should be atomic; this is only a last-resort fallback.
            file.writeBytes(privateKeyPem)
            temp.delete()
        }
    }

    override fun clear() {
        file.delete()
        File(file.parentFile, "${file.name}.tmp").delete()
    }
}
