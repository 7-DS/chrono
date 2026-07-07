package com.chrono.ssh.core.service

import com.hierynomus.sshj.key.BaseKeyAlgorithm
import com.hierynomus.sshj.key.KeyAlgorithm
import java.io.StringReader
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.security.AlgorithmParameters
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPrivateCrtKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.EncryptedPrivateKeyInfo
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.Factory
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.common.SSHRuntimeException
import net.schmizz.sshj.signature.Signature as SshjSignature
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import org.bouncycastle.openssl.PEMEncryptedKeyPair
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.jce.provider.BouncyCastleProvider

internal object AndroidSshjCompat {
    private const val BOUNCY_CASTLE_PROVIDER_NAME = "BC"
    private const val ED25519_OID = "1.3.101.112"
    private const val ED25519_KEY_SIZE = 32
    private const val OPENSSH_ED25519 = "ssh-ed25519"
    private const val OPENSSH_RSA = "ssh-rsa"
    private const val OPENSSH_ECDSA_PREFIX = "ecdsa-sha2-"
    private const val OPENSSH_PRIVATE_MAGIC = "openssh-key-v1\u0000"
    private val providerLock = Any()
    private val pkcs8PrivateKeyHeader = Base64.getDecoder().decode("MC4CAQEwBQYDK2VwBCIEIA")
    private val x509PublicKeyHeader = Base64.getDecoder().decode("MCowBQYDK2VwAyEA")

    fun prepare(config: DefaultConfig) {
        ensureBundledBouncyCastleProviderInstalled()
        val compatibleKex = config.keyExchangeFactories.filterNot { factory ->
            factory.name.contains("curve25519", ignoreCase = true)
        }
        if (compatibleKex.isNotEmpty()) {
            config.keyExchangeFactories = compatibleKex
        }
        val compatibleHostKeys = config.keyAlgorithms
            .map { factory ->
                androidCompatibleEcdsaHostKeyFactory(factory.name)
                    ?: androidCompatibleEd25519KeyFactory(factory.name)
                    ?: factory
            }
            .sortedBy { factory -> androidHostKeyPriority(factory.name) }
        if (compatibleHostKeys.isNotEmpty()) {
            config.keyAlgorithms = compatibleHostKeys
        }
    }

    fun loadPkcs8Ed25519Identity(
        @Suppress("UNUSED_PARAMETER") client: SSHClient,
        privateKeyMaterial: String,
        publicKeyMaterial: String?,
        passphrase: String?
    ): KeyProvider? {
        val pem = parsePem(privateKeyMaterial) ?: return null
        val encodedPkcs8 = when (pem.type) {
            "PRIVATE KEY" -> PKCS8EncodedKeySpec(pem.body).encoded
            "ENCRYPTED PRIVATE KEY" -> decryptPkcs8PrivateKey(pem.body, passphrase)?.encoded
            else -> null
        } ?: return null
        val seed = extractEd25519Seed(encodedPkcs8) ?: return null
        val rawPublicKey = Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded
        parseOpenSshPublicKey(publicKeyMaterial)
        return Ed25519KeyProvider(
            publicKey = StableEd25519PublicKey(rawPublicKey),
            privateKey = StableEd25519PrivateKey(seed)
        )
    }

    fun loadOpenSshIdentity(
        @Suppress("UNUSED_PARAMETER") client: SSHClient,
        privateKeyMaterial: String,
        @Suppress("UNUSED_PARAMETER") passphrase: String?
    ): KeyProvider? {
        val pem = parsePem(privateKeyMaterial) ?: return null
        if (pem.type != "OPENSSH PRIVATE KEY") return null
        return parseUnencryptedOpenSshPrivateKey(pem.body)
    }

    fun loadPemRsaIdentity(
        @Suppress("UNUSED_PARAMETER") client: SSHClient,
        privateKeyMaterial: String,
        passphrase: String?
    ): KeyProvider? {
        ensureBundledBouncyCastleProviderInstalled()
        val parsed = runCatching { PEMParser(StringReader(privateKeyMaterial)).use { it.readObject() } }.getOrNull()
            ?: return null
        val converter = JcaPEMKeyConverter().setProvider(BOUNCY_CASTLE_PROVIDER_NAME)
        val keyPair = when (parsed) {
            is PEMKeyPair -> runCatching { converter.getKeyPair(parsed) }.getOrNull()
            is PEMEncryptedKeyPair -> {
                if (passphrase.isNullOrEmpty()) return null
                runCatching {
                    val decryptor = JcePEMDecryptorProviderBuilder()
                        .setProvider(BOUNCY_CASTLE_PROVIDER_NAME)
                        .build(passphrase.toCharArray())
                    converter.getKeyPair(parsed.decryptKeyPair(decryptor))
                }.getOrNull()
            }
            is PrivateKeyInfo -> runCatching {
                val privateKey = converter.getPrivateKey(parsed)
                val publicKey = derivePublicKey(privateKey) ?: return null
                KeyPair(publicKey, privateKey)
            }.getOrNull()
            else -> null
        } ?: return null
        val publicKey = keyPair.public as? RSAPublicKey ?: return null
        val privateKey = keyPair.private as? RSAPrivateKey ?: return null
        return StaticKeyProvider(publicKey, privateKey, KeyType.RSA)
    }

    fun authorizedPublicKeyLine(
        privateKeyMaterial: String,
        passphrase: String? = null,
        comment: String = "chronossh"
    ): String? {
        openSshAuthorizedPublicKeyLine(privateKeyMaterial, comment)?.let { return it }
        val provider = loadOpenSshIdentity(SSHClient(), privateKeyMaterial, passphrase)
            ?: loadPemRsaIdentity(SSHClient(), privateKeyMaterial, passphrase)
            ?: loadPkcs8Ed25519Identity(SSHClient(), privateKeyMaterial, null, passphrase)
            ?: return null
        val keyType = provider.type
        val blob = when (keyType) {
            KeyType.ED25519 -> {
                val raw = extractEd25519PublicKey(provider.public)
                sshString(OPENSSH_ED25519) + sshString(raw)
            }
            KeyType.RSA -> {
                val publicKey = provider.public as? RSAPublicKey ?: return null
                sshString(OPENSSH_RSA) + sshMpInt(publicKey.publicExponent) + sshMpInt(publicKey.modulus)
            }
            KeyType.ECDSA256, KeyType.ECDSA384, KeyType.ECDSA521 -> {
                val publicKey = provider.public as? ECPublicKey ?: return null
                val (type, curve) = when (keyType) {
                    KeyType.ECDSA256 -> "ecdsa-sha2-nistp256" to "nistp256"
                    KeyType.ECDSA384 -> "ecdsa-sha2-nistp384" to "nistp384"
                    KeyType.ECDSA521 -> "ecdsa-sha2-nistp521" to "nistp521"
                    else -> return null
                }
                sshString(type) + sshString(curve) + sshString(encodeUncompressedEcPoint(publicKey))
            }
            else -> return null
        }
        val safeComment = comment.trim().replace(Regex("\\s+"), "-").ifBlank { "chronossh" }
        return "${keyType.toString()} ${Base64.getEncoder().encodeToString(blob)} $safeComment"
    }

    private fun openSshAuthorizedPublicKeyLine(privateKeyMaterial: String, comment: String): String? {
        val pem = parsePem(privateKeyMaterial) ?: return null
        if (pem.type != "OPENSSH PRIVATE KEY") return null
        return runCatching {
            val outer = ByteReader(pem.body)
            outer.expectBytes(OPENSSH_PRIVATE_MAGIC.toByteArray(Charsets.UTF_8))
            outer.readStringText()
            outer.readStringText()
            outer.readStringBytes()
            val keyCount = outer.readUInt32()
            if (keyCount < 1) return null
            val publicBlob = outer.readStringBytes()
            val keyType = Buffer.PlainBuffer(publicBlob).readString()
            if (keyType != OPENSSH_ED25519 && keyType != OPENSSH_RSA && !keyType.startsWith(OPENSSH_ECDSA_PREFIX)) return null
            val safeComment = comment.trim().replace(Regex("\\s+"), "-").ifBlank { "chronossh" }
            "$keyType ${Base64.getEncoder().encodeToString(publicBlob)} $safeComment"
        }.getOrNull()
    }

    private fun ensureBundledBouncyCastleProviderInstalled() {
        synchronized(providerLock) {
            runCatching {
                val existing = Security.getProvider(BOUNCY_CASTLE_PROVIDER_NAME)
                if (existing?.javaClass?.name != BouncyCastleProvider::class.java.name) {
                    if (existing != null) Security.removeProvider(existing.name)
                    Security.addProvider(BouncyCastleProvider())
                }
            }
        }
    }

    private fun androidCompatibleEcdsaHostKeyFactory(name: String): Factory.Named<KeyAlgorithm>? {
        return when (name) {
            KeyType.ECDSA256.toString() -> AndroidEcdsaKeyAlgorithmFactory(name, "SHA256withECDSA", KeyType.ECDSA256)
            KeyType.ECDSA256_CERT.toString() -> AndroidEcdsaKeyAlgorithmFactory(name, "SHA256withECDSA", KeyType.ECDSA256_CERT)
            KeyType.ECDSA384.toString() -> AndroidEcdsaKeyAlgorithmFactory(name, "SHA384withECDSA", KeyType.ECDSA384)
            KeyType.ECDSA384_CERT.toString() -> AndroidEcdsaKeyAlgorithmFactory(name, "SHA384withECDSA", KeyType.ECDSA384_CERT)
            KeyType.ECDSA521.toString() -> AndroidEcdsaKeyAlgorithmFactory(name, "SHA512withECDSA", KeyType.ECDSA521)
            KeyType.ECDSA521_CERT.toString() -> AndroidEcdsaKeyAlgorithmFactory(name, "SHA512withECDSA", KeyType.ECDSA521_CERT)
            else -> null
        }
    }

    private fun androidCompatibleEd25519KeyFactory(name: String): Factory.Named<KeyAlgorithm>? {
        return when (name) {
            KeyType.ED25519.toString() -> AndroidEd25519KeyAlgorithmFactory(name, KeyType.ED25519)
            else -> null
        }
    }

    private fun androidHostKeyPriority(name: String): Int {
        val lower = name.lowercase()
        return when {
            lower == "rsa-sha2-512" -> 0
            lower == "rsa-sha2-256" -> 1
            lower.contains("rsa") -> 2
            lower.contains("ecdsa") -> 3
            lower.contains("ed25519") || lower.contains("eddsa") -> 4
            else -> 5
        }
    }

    private class AndroidEcdsaKeyAlgorithmFactory(
        private val name: String,
        private val jcaAlgorithm: String,
        private val keyType: KeyType
    ) : Factory.Named<KeyAlgorithm> {
        override fun getName(): String = name
        override fun create(): KeyAlgorithm = BaseKeyAlgorithm(name, AndroidEcdsaSignatureFactory(name, jcaAlgorithm), keyType)
    }

    private class AndroidEd25519KeyAlgorithmFactory(
        private val name: String,
        private val keyType: KeyType
    ) : Factory.Named<KeyAlgorithm> {
        override fun getName(): String = name
        override fun create(): KeyAlgorithm = BaseKeyAlgorithm(name, AndroidEd25519SignatureFactory(name), keyType)
    }

    private class AndroidEcdsaSignatureFactory(
        private val name: String,
        private val jcaAlgorithm: String
    ) : Factory.Named<SshjSignature> {
        override fun getName(): String = name
        override fun create(): SshjSignature = AndroidEcdsaSignature(name, jcaAlgorithm)
    }

    private class AndroidEd25519SignatureFactory(private val name: String) : Factory.Named<SshjSignature> {
        override fun getName(): String = name
        override fun create(): SshjSignature = AndroidEd25519Signature(name)
    }

    private class AndroidEcdsaSignature(
        private val signatureName: String,
        private val jcaAlgorithm: String
    ) : SshjSignature {
        private val signature = runCatching {
            Security.getProvider(BOUNCY_CASTLE_PROVIDER_NAME)
                ?.let { Signature.getInstance(jcaAlgorithm, it) }
                ?: Signature.getInstance(jcaAlgorithm)
        }.getOrElse { throw SSHRuntimeException(it) }

        override fun getSignatureName(): String = signatureName
        override fun initVerify(publicKey: PublicKey) = runCatching { signature.initVerify(publicKey) }.getOrElse { throw SSHRuntimeException(it) }
        override fun initSign(privateKey: PrivateKey) = runCatching { signature.initSign(privateKey) }.getOrElse { throw SSHRuntimeException(it) }
        override fun update(data: ByteArray) = update(data, 0, data.size)
        override fun update(data: ByteArray, offset: Int, length: Int) = runCatching { signature.update(data, offset, length) }.getOrElse { throw SSHRuntimeException(it) }
        override fun sign(): ByteArray = runCatching { signature.sign() }.getOrElse { throw SSHRuntimeException(it) }

        override fun encode(signature: ByteArray): ByteArray {
            val (r, s) = runCatching { derDecodeEcdsaSignature(signature) }.getOrElse { throw SSHRuntimeException(it) }
            return Buffer.PlainBuffer().putMPInt(r).putMPInt(s).compactData
        }

        override fun verify(signature: ByteArray): Boolean {
            val derSignature = runCatching { sshEcdsaSignatureToDer(signature, signatureName) }.getOrElse { throw SSHRuntimeException(it) }
            return runCatching { this.signature.verify(derSignature) }.getOrElse { throw SSHRuntimeException(it) }
        }
    }

    private class AndroidEd25519Signature(private val signatureName: String) : SshjSignature {
        private var signer: Ed25519Signer? = null

        override fun getSignatureName(): String = signatureName
        override fun initVerify(publicKey: PublicKey) {
            val rawPublicKey = runCatching { extractEd25519PublicKey(publicKey) }.getOrElse { throw SSHRuntimeException(it) }
            signer = Ed25519Signer().apply { init(false, Ed25519PublicKeyParameters(rawPublicKey, 0)) }
        }

        override fun initSign(privateKey: PrivateKey) {
            val seed = runCatching { extractEd25519PrivateSeed(privateKey) }.getOrElse { throw SSHRuntimeException(it) }
            signer = Ed25519Signer().apply { init(true, Ed25519PrivateKeyParameters(seed, 0)) }
        }

        override fun update(data: ByteArray) = update(data, 0, data.size)
        override fun update(data: ByteArray, offset: Int, length: Int) = runCatching {
            signer?.update(data, offset, length) ?: error("Ed25519 signature is not initialized.")
        }.getOrElse { throw SSHRuntimeException(it) }

        override fun sign(): ByteArray = runCatching {
            signer?.generateSignature() ?: error("Ed25519 signature is not initialized.")
        }.getOrElse { throw SSHRuntimeException(it) }

        override fun encode(signature: ByteArray): ByteArray = signature
        override fun verify(signature: ByteArray): Boolean {
            val rawSignature = runCatching { extractEd25519Signature(signature, signatureName) }.getOrElse { throw SSHRuntimeException(it) }
            return runCatching {
                signer?.verifySignature(rawSignature) ?: error("Ed25519 signature is not initialized.")
            }.getOrElse { throw SSHRuntimeException(it) }
        }
    }

    private fun extractEd25519PrivateSeed(privateKey: PrivateKey): ByteArray {
        if (privateKey is RawEd25519PrivateKey) return privateKey.rawSeed()
        val info = PrivateKeyInfo.getInstance(privateKey.encoded)
        check(info.privateKeyAlgorithm.algorithm.id == ED25519_OID) { "Private key is not Ed25519." }
        val octets = ASN1OctetString.getInstance(info.parsePrivateKey()).octets
        check(octets.size == ED25519_KEY_SIZE) { "Invalid Ed25519 private key length." }
        return octets
    }

    private fun extractEd25519PublicKey(publicKey: PublicKey): ByteArray {
        if (publicKey is RawEd25519PublicKey) return publicKey.rawPublicKey()
        val encoded = publicKey.encoded
        check(encoded.size >= ED25519_KEY_SIZE) { "Invalid Ed25519 public key length." }
        return encoded.copyOfRange(encoded.size - ED25519_KEY_SIZE, encoded.size)
    }

    private fun extractEd25519Signature(signature: ByteArray, expectedName: String): ByteArray {
        if (signature.size == 64) return signature
        val buffer = Buffer.PlainBuffer(signature)
        val actualName = buffer.readString()
        check(actualName == expectedName) { "Expected '$expectedName' key algorithm, but got: $actualName" }
        return buffer.readBytes().also { check(it.size == 64) { "Invalid Ed25519 signature length." } }
    }

    private fun sshEcdsaSignatureToDer(signature: ByteArray, expectedName: String): ByteArray {
        val outer = Buffer.PlainBuffer(signature)
        val actualName = outer.readString()
        check(actualName == expectedName) { "Expected '$expectedName' key algorithm, but got: $actualName" }
        val inner = Buffer.PlainBuffer(outer.readBytes())
        return derEncodeEcdsaSignature(inner.readMPInt(), inner.readMPInt())
    }

    private fun derEncodeEcdsaSignature(r: BigInteger, s: BigInteger): ByteArray {
        val encodedR = derEncodeInteger(r)
        val encodedS = derEncodeInteger(s)
        return byteArrayOf(0x30) + derEncodeLength(encodedR.size + encodedS.size) + encodedR + encodedS
    }

    private fun derEncodeInteger(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        return byteArrayOf(0x02) + derEncodeLength(bytes.size) + bytes
    }

    private fun derDecodeEcdsaSignature(signature: ByteArray): Pair<BigInteger, BigInteger> {
        val reader = DerReader(signature)
        reader.expect(0x30)
        val sequenceEnd = reader.position + reader.readLength()
        val r = reader.readInteger()
        val s = reader.readInteger()
        check(reader.position == sequenceEnd) { "Unexpected trailing ECDSA signature data." }
        return r to s
    }

    private fun derEncodeLength(length: Int): ByteArray {
        if (length < 0x80) return byteArrayOf(length.toByte())
        val bytes = BigInteger.valueOf(length.toLong()).toByteArray().dropWhile { it == 0.toByte() }
        return byteArrayOf((0x80 or bytes.size).toByte()) + bytes.toByteArray()
    }

    private fun parsePem(input: String): PemBlock? {
        val match = pemRegex.find(input.trim()) ?: return null
        val body = match.groupValues[2].replace(Regex("\\s"), "")
        return runCatching {
            PemBlock(match.groupValues[1].trim(), Base64.getDecoder().decode(body))
        }.getOrNull()
    }

    private fun decryptPkcs8PrivateKey(body: ByteArray, passphrase: String?): PKCS8EncodedKeySpec? {
        if (passphrase.isNullOrBlank()) return null
        return runCatching {
            val encryptedInfo = EncryptedPrivateKeyInfo(body)
            val secretKey = SecretKeyFactory.getInstance(encryptedInfo.algName)
                .generateSecret(PBEKeySpec(passphrase.toCharArray()))
            val cipher = Cipher.getInstance(encryptedInfo.algName)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, encryptedInfo.algParameters)
            encryptedInfo.getKeySpec(cipher)
        }.getOrNull()
    }

    private fun extractEd25519Seed(encodedPkcs8: ByteArray): ByteArray? {
        return runCatching {
            val info = PrivateKeyInfo.getInstance(encodedPkcs8)
            if (info.privateKeyAlgorithm.algorithm.id != ED25519_OID) return null
            ASN1OctetString.getInstance(info.parsePrivateKey()).octets.takeIf { it.size == ED25519_KEY_SIZE }
        }.getOrNull()
    }

    private fun parseOpenSshPublicKey(publicKeyMaterial: String?): ByteArray? {
        val parts = publicKeyMaterial
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$OPENSSH_ED25519 ") }
            ?.split(Regex("\\s+"), limit = 3)
            ?: return null
        if (parts.size < 2) return null
        return runCatching {
            val buffer = Buffer.PlainBuffer(Base64.getDecoder().decode(parts[1]))
            if (buffer.readString() != OPENSSH_ED25519) return null
            buffer.readBytes().takeIf { it.size == ED25519_KEY_SIZE }
        }.getOrNull()
    }

    private fun parseUnencryptedOpenSshPrivateKey(bytes: ByteArray): KeyProvider? {
        return runCatching {
            val outer = ByteReader(bytes)
            outer.expectBytes(OPENSSH_PRIVATE_MAGIC.toByteArray(Charsets.UTF_8))
            val cipherName = outer.readStringText()
            val kdfName = outer.readStringText()
            outer.readStringBytes()
            val keyCount = outer.readUInt32()
            if (cipherName != "none" || kdfName != "none" || keyCount < 1) return null
            repeat(keyCount) { outer.readStringBytes() }
            val privateBlob = ByteReader(outer.readStringBytes())
            val check1 = privateBlob.readUInt32()
            val check2 = privateBlob.readUInt32()
            if (check1 != check2) return null
            when (privateBlob.readStringText()) {
                OPENSSH_ED25519 -> parseOpenSshEd25519Private(privateBlob)
                OPENSSH_RSA -> parseOpenSshRsaPrivate(privateBlob)
                else -> parseOpenSshEcdsaPrivate(privateBlob)
            }
        }.getOrNull()
    }

    private fun parseOpenSshEd25519Private(reader: ByteReader): KeyProvider? {
        val publicKey = reader.readStringBytes().takeIf { it.size == ED25519_KEY_SIZE } ?: return null
        val privateKey = reader.readStringBytes().takeIf { it.size >= ED25519_KEY_SIZE } ?: return null
        val seed = privateKey.copyOfRange(0, ED25519_KEY_SIZE)
        return Ed25519KeyProvider(
            publicKey = StableEd25519PublicKey(publicKey),
            privateKey = StableEd25519PrivateKey(seed)
        )
    }

    private fun parseOpenSshRsaPrivate(reader: ByteReader): KeyProvider? {
        val modulus = reader.readMPInt()
        val publicExponent = reader.readMPInt()
        val privateExponent = reader.readMPInt()
        val crtCoefficient = reader.readMPInt()
        val primeP = reader.readMPInt()
        val primeQ = reader.readMPInt()
        val primeExponentP = privateExponent.mod(primeP.subtract(BigInteger.ONE))
        val primeExponentQ = privateExponent.mod(primeQ.subtract(BigInteger.ONE))
        val factory = KeyFactory.getInstance("RSA")
        val privateKey = factory.generatePrivate(
            RSAPrivateCrtKeySpec(
                modulus,
                publicExponent,
                privateExponent,
                primeP,
                primeQ,
                primeExponentP,
                primeExponentQ,
                crtCoefficient
            )
        )
        val publicKey = factory.generatePublic(RSAPublicKeySpec(modulus, publicExponent))
        return StaticKeyProvider(publicKey, privateKey, KeyType.RSA)
    }

    private fun parseOpenSshEcdsaPrivate(reader: ByteReader): KeyProvider? {
        val keyType = reader.previousStringText ?: return null
        if (!keyType.startsWith(OPENSSH_ECDSA_PREFIX)) return null
        val curveName = reader.readStringText()
        val publicPointBytes = reader.readStringBytes()
        val privateScalar = reader.readMPInt()
        val keyTypeEnum = when (curveName) {
            "nistp256" -> KeyType.ECDSA256
            "nistp384" -> KeyType.ECDSA384
            "nistp521" -> KeyType.ECDSA521
            else -> return null
        }
        val jcaCurve = when (curveName) {
            "nistp256" -> "secp256r1"
            "nistp384" -> "secp384r1"
            "nistp521" -> "secp521r1"
            else -> return null
        }
        val params = ecParameters(jcaCurve)
        val publicPoint = decodeUncompressedEcPoint(publicPointBytes) ?: return null
        val factory = KeyFactory.getInstance("EC")
        val publicKey = factory.generatePublic(ECPublicKeySpec(publicPoint, params))
        val privateKey = factory.generatePrivate(ECPrivateKeySpec(privateScalar, params))
        return StaticKeyProvider(publicKey, privateKey, keyTypeEnum)
    }

    private fun ecParameters(curveName: String): ECParameterSpec {
        val parameters = AlgorithmParameters.getInstance("EC")
        parameters.init(ECGenParameterSpec(curveName))
        return parameters.getParameterSpec(ECParameterSpec::class.java)
    }

    private fun decodeUncompressedEcPoint(bytes: ByteArray): ECPoint? {
        if (bytes.size < 3 || bytes[0] != 0x04.toByte()) return null
        val coordinateLength = (bytes.size - 1) / 2
        if (coordinateLength <= 0 || bytes.size != 1 + coordinateLength * 2) return null
        val x = BigInteger(1, bytes.copyOfRange(1, 1 + coordinateLength))
        val y = BigInteger(1, bytes.copyOfRange(1 + coordinateLength, bytes.size))
        return ECPoint(x, y)
    }

    private fun encodeUncompressedEcPoint(publicKey: ECPublicKey): ByteArray {
        val fieldBytes = (publicKey.params.curve.field.fieldSize + 7) / 8
        return byteArrayOf(0x04) +
            publicKey.w.affineX.toFixedBytes(fieldBytes) +
            publicKey.w.affineY.toFixedBytes(fieldBytes)
    }

    private fun derivePublicKey(privateKey: PrivateKey): PublicKey? {
        return when (privateKey) {
            is RSAPrivateCrtKey -> KeyFactory.getInstance("RSA").generatePublic(
                RSAPublicKeySpec(privateKey.modulus, privateKey.publicExponent)
            )
            else -> null
        }
    }

    private interface RawEd25519PrivateKey {
        fun rawSeed(): ByteArray
    }

    private interface RawEd25519PublicKey {
        fun rawPublicKey(): ByteArray
    }

    private class Ed25519KeyProvider(
        private val publicKey: PublicKey,
        private val privateKey: PrivateKey
    ) : KeyProvider {
        override fun getPrivate(): PrivateKey = privateKey
        override fun getPublic(): PublicKey = publicKey
        override fun getType(): KeyType = KeyType.ED25519
    }

    private class StaticKeyProvider(
        private val publicKey: PublicKey,
        private val privateKey: PrivateKey,
        private val keyType: KeyType
    ) : KeyProvider {
        override fun getPrivate(): PrivateKey = privateKey
        override fun getPublic(): PublicKey = publicKey
        override fun getType(): KeyType = keyType
    }

    private class StableEd25519PrivateKey(private val seed: ByteArray) : PrivateKey, RawEd25519PrivateKey {
        override fun getAlgorithm(): String = "Ed25519"
        override fun getFormat(): String = "PKCS#8"
        override fun getEncoded(): ByteArray = pkcs8PrivateKeyHeader + seed.copyOf()
        override fun rawSeed(): ByteArray = seed.copyOf()
    }

    private class StableEd25519PublicKey(private val publicKey: ByteArray) : PublicKey, RawEd25519PublicKey {
        override fun getAlgorithm(): String = "Ed25519"
        override fun getFormat(): String = "X.509"
        override fun getEncoded(): ByteArray = x509PublicKeyHeader + publicKey.copyOf()
        override fun rawPublicKey(): ByteArray = publicKey.copyOf()
    }

    private class DerReader(private val data: ByteArray) {
        var position: Int = 0
            private set

        fun expect(expected: Int) {
            val actual = readByte()
            check(actual == expected) { "Expected DER tag 0x${expected.toString(16)}, got 0x${actual.toString(16)}." }
        }

        fun readLength(): Int {
            val first = readByte()
            if ((first and 0x80) == 0) return first
            val byteCount = first and 0x7F
            check(byteCount in 1..4) { "Unsupported DER length." }
            var length = 0
            repeat(byteCount) { length = (length shl 8) or readByte() }
            check(length >= 0 && position + length <= data.size) { "Invalid DER length." }
            return length
        }

        fun readInteger(): BigInteger {
            expect(0x02)
            val length = readLength()
            check(length > 0 && position + length <= data.size) { "Invalid DER integer length." }
            val bytes = data.copyOfRange(position, position + length)
            position += length
            return BigInteger(bytes)
        }

        private fun readByte(): Int {
            check(position < data.size) { "Unexpected end of DER data." }
            return data[position++].toInt() and 0xFF
        }
    }

    private class ByteReader(private val data: ByteArray) {
        private var position: Int = 0
        var previousStringText: String? = null
            private set

        fun expectBytes(expected: ByteArray) {
            check(position + expected.size <= data.size) { "Unexpected end of OpenSSH key." }
            expected.forEachIndexed { offset, byte ->
                check(data[position + offset] == byte) { "OpenSSH key magic mismatch." }
            }
            position += expected.size
        }

        fun readUInt32(): Int {
            check(position + 4 <= data.size) { "Unexpected end of OpenSSH key." }
            val value = ((data[position].toInt() and 0xFF) shl 24) or
                ((data[position + 1].toInt() and 0xFF) shl 16) or
                ((data[position + 2].toInt() and 0xFF) shl 8) or
                (data[position + 3].toInt() and 0xFF)
            position += 4
            return value
        }

        fun readStringText(): String = readStringBytes().toString(Charsets.UTF_8).also {
            previousStringText = it
        }

        fun readStringBytes(): ByteArray {
            val length = readUInt32()
            check(length >= 0 && position + length <= data.size) { "Invalid OpenSSH key string length." }
            return data.copyOfRange(position, position + length).also { position += length }
        }

        fun readMPInt(): BigInteger {
            val bytes = readStringBytes()
            return if (bytes.isEmpty()) BigInteger.ZERO else BigInteger(bytes)
        }
    }

    private data class PemBlock(val type: String, val body: ByteArray)

    private fun sshString(value: String): ByteArray = sshString(value.toByteArray(Charsets.UTF_8))

    private fun sshString(bytes: ByteArray): ByteArray = uint32(bytes.size) + bytes

    private fun sshMpInt(value: BigInteger): ByteArray = sshString(value.toByteArray())

    private fun uint32(value: Int): ByteArray {
        return byteArrayOf(
            ((value ushr 24) and 0xFF).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }

    private fun BigInteger.toFixedBytes(length: Int): ByteArray {
        val raw = toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return ByteArray((length - raw.size).coerceAtLeast(0)) + raw.takeLast(length).toByteArray()
    }

    private val pemRegex = Regex(
        pattern = "-----BEGIN ([A-Z0-9 ]+)-----(.*?)-----END \\1-----",
        options = setOf(RegexOption.DOT_MATCHES_ALL)
    )
}
