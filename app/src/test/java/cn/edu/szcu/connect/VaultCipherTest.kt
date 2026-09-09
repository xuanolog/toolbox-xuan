package cn.edu.szcu.connect

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.KeyGenerator

class VaultCipherTest {
    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    @Test fun encryptedRoundTripAndUniqueNonces() {
        val key = key(); val text = "虚构账号:student0001;虚构密码:a&b=+".toByteArray()
        val a = VaultCipher.encrypt(key, text); val b = VaultCipher.encrypt(key, text)
        assertArrayEquals(text, VaultCipher.decrypt(key, a))
        assertFalse(a.contentEquals(b)); assertFalse(String(a).contains("student0001"))
    }
    @Test fun modifiedCiphertextIsRejected() {
        val key = key(); val encrypted = VaultCipher.encrypt(key, "test".toByteArray())
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { VaultCipher.decrypt(key, encrypted) }
    }
    @Test fun differentKeyCannotDecrypt() { assertThrows(Exception::class.java) { VaultCipher.decrypt(key(), VaultCipher.encrypt(key(), "test".toByteArray())) } }
    @Test fun invalidEnvelopeRejected() { assertThrows(IllegalArgumentException::class.java) { VaultCipher.decrypt(key(), byteArrayOf(1, 2)) } }
}
