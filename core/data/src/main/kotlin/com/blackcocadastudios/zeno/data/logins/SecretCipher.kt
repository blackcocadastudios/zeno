package com.blackcocadastudios.zeno.data.logins

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the secrets kept on disk (logins' usernames and passwords). */
interface SecretCipher {
    fun encrypt(plain: String): String

    /** Null when it cannot be read back: the key is gone, or the text was altered. */
    fun decrypt(sealed: String): String?
}

/**
 * AES-256-GCM with a key that never leaves Android's keystore (hardware-backed
 * where the phone has it). Every value gets its own random IV, chosen by the
 * keystore. Opening the database file elsewhere reveals no username or password.
 *
 * The key needs no unlock, so forms can be filled without asking; showing
 * passwords in the settings asks for the screen lock instead.
 */
class KeystoreCipher(private val alias: String = "zeno-logins") : SecretCipher {
    private val key: SecretKey by lazy {
        existing() ?: generate().also { Log.i(TAG, "Created the key for saved logins") }
    }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    override fun decrypt(sealed: String): String? = runCatching {
        require(sealed.startsWith(PREFIX))
        val bytes = Base64.decode(sealed.substring(PREFIX.length), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }.onFailure { Log.w(TAG, "A saved value could not be decrypted", it) }.getOrNull()

    private fun existing(): SecretKey? {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun generate(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private companion object {
        const val TAG = "Zeno"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"

        /** Format version, so the scheme can change without guessing. */
        const val PREFIX = "v1:"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
