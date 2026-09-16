package org.kazumi.tv.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-local encrypted storage; neither APK nor source contains an application secret. */
class WebDavCredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("webdav_credentials", Context.MODE_PRIVATE)
    private val alias = "kazumitv.webdav.credentials"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(): WebDavAccount? {
        val encoded = prefs.getString("value", null) ?: return null
        return runCatching {
            val parts = encoded.split(':')
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            val json = JSONObject(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8))
            WebDavAccount(json.getString("directory"), json.getString("username"), json.getString("password"))
        }.getOrNull()
    }
    fun save(value: WebDavAccount) {
        WebDavCollections(value)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = JSONObject().put("directory",value.directory).put("username",value.username).put("password",value.password).toString().toByteArray(Charsets.UTF_8)
        val data = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(payload), Base64.NO_WRAP)
        check(prefs.edit().putString("value", data).commit()) { "凭证保存失败" }
    }
    fun clear() { check(prefs.edit().clear().commit()) { "配置移除失败" } }
}
