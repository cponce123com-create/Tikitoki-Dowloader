package com.tikitoki.downloader.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** On-disk AES-GCM envelope consisting of a random IV followed by ciphertext and authentication tag. */
internal object EncryptedPhotoSession {
    private const val STORE_FILE = "photo-session-v1.enc"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "bajape.photo-session.v1"
    private const val IV_BYTES = 12
    private const val TAG_BYTES = 16
    private const val KEY_BITS = 256

    fun exists(context: Context): Boolean = store(context).baseFile.exists()

    fun read(
        context: Context,
        maxBytes: Int,
    ): String {
        val file = store(context)
        require(file.baseFile.length() in (IV_BYTES + TAG_BYTES).toLong()..maxBytes.toLong())
        val bytes = file.readFully()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = checkNotNull(keyStore().getKey(KEY_ALIAS, null) as? SecretKey)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * Byte.SIZE_BITS, bytes, 0, IV_BYTES))
        return cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES).toString(Charsets.UTF_8)
    }

    fun delete(context: Context) {
        val file = store(context)
        file.delete()
        keyStore().deleteEntry(KEY_ALIAS)
        check(!file.baseFile.exists())
    }

    fun write(
        context: Context,
        contents: String,
    ) {
        val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey ?: generateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        check(cipher.iv.size == IV_BYTES)
        val bytes = cipher.iv + cipher.doFinal(contents.toByteArray(Charsets.UTF_8))
        val file = store(context)
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: IOException) {
            file.failWrite(output)
            throw error
        }
        check(file.readFully().contentEquals(bytes))
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun generateKey(): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec
                    .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_BITS)
                    .build(),
            )
            generateKey()
        }

    private fun store(context: Context): AtomicFile = AtomicFile(File(context.noBackupFilesDir, STORE_FILE))
}
