package cn.edu.szcu.connect

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.google.gson.Gson
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class Carrier(val label: String, val suffix: String) {
    TELECOM("中国电信", "@telecom"), MOBILE("中国移动", "@cmcc"),
    UNICOM("中国联通", "@unicom"), CAMPUS("校园内网", "")
}

data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ssid: String = "SZCU-313-5G",
    val carrier: Carrier,
    val account: String,
    val password: String
) {
    override fun toString() = "Profile(redacted)"
    fun validate() {
        require(name.isNotBlank() && name.length <= 60) { "请输入 1–60 字的配置名称" }
        require(ssid.isNotBlank() && ssid.toByteArray().size <= 32) { "Wi-Fi 名称应为 1–32 字节" }
        require(account.matches(Regex("[A-Za-z0-9_.-]{1,64}"))) { "学工号请填写字母、数字、点、横线或下划线，不要添加运营商后缀" }
        require(password.isNotEmpty() && password.length <= 256) { "请输入密码（最多 256 字符）" }
    }
}

data class ProfileBook(val version: Int = 1, val selectedId: String? = null, val profiles: List<Profile> = emptyList())

/** Version byte + 12-byte nonce + GCM ciphertext/tag. AAD prevents cross-format reuse. */
object VaultCipher {
    private val aad = "SZCUConnect/profiles/v1".toByteArray()
    fun encrypt(key: SecretKey, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad)
        return byteArrayOf(1) + cipher.iv + cipher.doFinal(plain)
    }
    fun decrypt(key: SecretKey, bytes: ByteArray): ByteArray {
        require(bytes.size >= 29 && bytes[0] == 1.toByte()) { "配置文件格式不受支持" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD(aad)
        return cipher.doFinal(bytes.copyOfRange(13, bytes.size))
    }
}

class ProfileStore(context: Context, fileName: String = "profiles.vault", private val alias: String = "szcu.profiles.v1") {
    private val file = AtomicFile(File(context.noBackupFilesDir, fileName))
    private val gson = Gson()
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    @Synchronized fun read(): ProfileBook {
        val bytes = try { file.readFully() } catch (_: java.io.FileNotFoundException) { return ProfileBook() }
        val plain = VaultCipher.decrypt(key(), bytes)
        return try {
            val book = gson.fromJson(String(plain, Charsets.UTF_8), ProfileBook::class.java)
            require(book.version == 1 && book.profiles.map { it.id }.distinct().size == book.profiles.size)
            book.profiles.forEach { it.validate() }
            require(book.selectedId == null || book.profiles.any { it.id == book.selectedId })
            book
        } finally { plain.fill(0) }
    }
    @Synchronized fun write(book: ProfileBook) {
        book.profiles.forEach { it.validate() }
        val plain = gson.toJson(book).toByteArray(Charsets.UTF_8)
        val encrypted = try { VaultCipher.encrypt(key(), plain) } finally { plain.fill(0) }
        val stream = file.startWrite()
        try { stream.write(encrypted); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
}
