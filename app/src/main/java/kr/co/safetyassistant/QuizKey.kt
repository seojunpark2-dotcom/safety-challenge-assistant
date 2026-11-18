package kr.co.safetyassistant

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object QuizKey {
    private const val alias = "quiz-gemini"
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun prefs(context: Context) = context.getSharedPreferences("quiz-key", Context.MODE_PRIVATE)
    private fun cipher() = Cipher.getInstance("AES/GCM/NoPadding")

    fun save(context: Context, value: String) {
        val key = store().getKey(alias, null) as? SecretKey
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
                generateKey()
            }
        val cipher = cipher()
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val data = Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
        check(prefs(context).edit().putString("encrypted", data).commit())
    }

    fun load(context: Context): String? {
        val encoded = prefs(context).getString("encrypted", null) ?: return null
        val key = store().getKey(alias, null) as? SecretKey ?: return null
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = cipher()
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data, 0, 12))
        return cipher.doFinal(data, 12, data.size - 12).decodeToString()
    }

    fun clear(context: Context) {
        check(prefs(context).edit().clear().commit())
    }
}
