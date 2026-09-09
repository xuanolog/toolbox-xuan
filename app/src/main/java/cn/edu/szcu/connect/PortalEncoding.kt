package cn.edu.szcu.connect

import java.nio.charset.Charset

object PortalEncoding {
    /** External portal scripts inherit the GB2312 document encoding when headers omit charset. */
    fun decode(bytes: ByteArray, contentType: String?, portal: Boolean): String {
        val declared = Regex("charset\\s*=\\s*[\"']?([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)
            .find(contentType.orEmpty())?.groupValues?.get(1)
        val meta = Regex("charset\\s*=\\s*[\"']?([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)
            .find(String(bytes.take(8192).toByteArray(), Charsets.ISO_8859_1))?.groupValues?.get(1)
        val fallback = if (portal) "GB18030" else "UTF-8"
        val charset = runCatching { Charset.forName(declared ?: meta ?: fallback) }
            .getOrElse { Charset.forName(fallback) }
        return String(bytes, charset)
    }
}
