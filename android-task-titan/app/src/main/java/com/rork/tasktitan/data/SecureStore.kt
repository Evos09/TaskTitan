package com.rork.tasktitan.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the whole app state as JSON encrypted with an AES-256-GCM key that lives
 * in the Android Keystore, so data at rest is never stored in plain text.
 */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("task_titan_secure", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun load(): AppData? {
        val iv = prefs.getString(KEY_IV, null) ?: return null
        val payload = prefs.getString(KEY_DATA, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            val text = String(cipher.doFinal(Base64.decode(payload, Base64.NO_WRAP)), Charsets.UTF_8)
            json.decodeFromString(AppData.serializer(), text)
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore saved data: ${e.javaClass.simpleName}")
            null
        }
    }

    fun save(data: AppData) {
        try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val bytes = cipher.doFinal(json.encodeToString(AppData.serializer(), data).toByteArray(Charsets.UTF_8))
            prefs.edit()
                .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString(KEY_DATA, Base64.encodeToString(bytes, Base64.NO_WRAP))
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Could not save data: ${e.javaClass.simpleName}")
        }
    }

    /** Readable export the user can share with themselves. */
    fun export(data: AppData): String = Json { prettyPrint = true; encodeDefaults = true }.encodeToString(AppData.serializer(), data)

    fun wipe() {
        prefs.edit().clear().apply()
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
        } catch (e: Exception) {
            Log.w(TAG, "Key removal failed: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "SecureStore"
        const val ALIAS = "task_titan_data_key"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val KEY_IV = "iv"
        const val KEY_DATA = "data"
    }
}
