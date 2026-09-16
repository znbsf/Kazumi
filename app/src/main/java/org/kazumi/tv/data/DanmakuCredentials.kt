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

class DanmakuCredentials(val appId: String, val secret: String) {
    override fun toString() = "DanmakuCredentials(redacted)"
}

/** User overrides are encrypted locally. Official previews may inject a build-time fallback. */
class DanmakuCredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("danmaku_credentials", Context.MODE_PRIVATE)
    private val alias = "kazumitv.dandan.credentials"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(): DanmakuCredentials? {
        val encoded = prefs.getString("value", null) ?: return bundled()

        return runCatching {
            val parts = encoded.split(':')
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            val json = JSONObject(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8))
            DanmakuCredentials(json.getString("id"), json.getString("secret"))
        }.getOrNull()
    }
    private fun bundled():DanmakuCredentials? {
        val id=org.kazumi.tv.BuildConfig.DANDAN_APP_ID
        val secret=org.kazumi.tv.BuildConfig.DANDAN_APP_SECRET
        return if(id.isNotBlank() && secret.isNotBlank())DanmakuCredentials(id,secret) else null
    }
    fun save(value: DanmakuCredentials) {
        require(value.appId.matches(Regex("[A-Za-z0-9_-]{2,128}")) && value.secret.length in 8..512) { "请检查 AppId 和 AppSecret" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = JSONObject().put("id", value.appId).put("secret", value.secret).toString().toByteArray(Charsets.UTF_8)
        val data = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(payload), Base64.NO_WRAP)
        check(prefs.edit().putString("value", data).commit()) { "凭证保存失败" }
    }
    fun clear() { prefs.edit().clear().apply() }
}
