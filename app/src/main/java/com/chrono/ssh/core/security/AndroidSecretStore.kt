package com.chrono.ssh.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.chrono.ssh.core.service.SecretStore
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidSecretStore(context: Context) : SecretStore {
    private val secretsDir = File(context.filesDir, "ChronoSSH-state/secrets").apply { mkdirs() }

    override suspend fun storeSecret(label: String, clearText: ByteArray): String = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(clearText)
        val safeLabel = label
            .lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
            .ifBlank { "secret" }
        val ref = "secret-$safeLabel-${UUID.randomUUID()}.v1"
        File(secretsDir, ref).writeText(
            listOf(
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
            ).joinToString("\n")
        )
        ref
    }

    override suspend fun loadSecret(ref: String): ByteArray = withContext(Dispatchers.IO) {
        val file = File(secretsDir, SecretRefPolicy.requireValid(ref))
        val lines = file.readLines()
        require(lines.size >= 2) { "Malformed secret payload: $ref" }
        val iv = Base64.decode(lines[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(lines[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.doFinal(encrypted)
    }

    override suspend fun deleteSecret(ref: String) = withContext(Dispatchers.IO) {
        File(secretsDir, SecretRefPolicy.requireValid(ref)).delete()
        Unit
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ChronoSSH.local.secrets.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}

object SecretRefPolicy {
    private val secretRefPattern = Regex(
        "^secret-[a-z0-9][a-z0-9-]*-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.v1$"
    )

    fun requireValid(ref: String): String {
        require(secretRefPattern.matches(ref)) { "Invalid secret reference." }
        return ref
    }
}
