package com.messark.dgchat

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.SecureRandom
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.math.ec.rfc7748.X25519
import java.nio.ByteBuffer

object Crypto {
    init {
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
    }

    fun generateKeyPair(): Pair<ByteArray, ByteArray> {
        val privateKey = ByteArray(32)
        SecureRandom().nextBytes(privateKey)
        val publicKey = ByteArray(32)
        X25519.scalarMultBase(privateKey, 0, publicKey, 0)
        return privateKey to publicKey
    }

    fun derivePublicKey(privateKey: ByteArray): ByteArray {
        val publicKey = ByteArray(32)
        X25519.scalarMultBase(privateKey, 0, publicKey, 0)
        return publicKey
    }

    fun ecdh(privateKey: ByteArray, otherPublicKey: ByteArray): ByteArray {
        val sharedSecret = ByteArray(32)
        X25519.scalarMult(privateKey, 0, otherPublicKey, 0, sharedSecret, 0)
        return sharedSecret
    }

    fun encryptGcm(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC")
        val secretKey = SecretKeySpec(key, "AES")
        val spec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        return cipher.doFinal(plaintext)
    }

    fun decryptGcm(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC")
        val secretKey = SecretKeySpec(key, "AES")
        val spec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(ciphertext)
    }

    fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, dkLen: Int): ByteArray {
        val gen = PKCS5S2ParametersGenerator(SHA256Digest())
        gen.init(password, salt, iterations)
        return (gen.generateDerivedParameters(dkLen * 8) as KeyParameter).key
    }

    fun hkdf(secret: ByteArray, salt: ByteArray, info: String, dkLen: Int): ByteArray {
        val gen = HKDFBytesGenerator(SHA256Digest())
        gen.init(HKDFParameters(secret, salt, info.toByteArray()))
        val result = ByteArray(dkLen)
        gen.generateBytes(result, 0, dkLen)
        return result
    }
}
