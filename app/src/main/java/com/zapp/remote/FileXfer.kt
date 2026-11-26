package com.zapp.remote

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/*
 * Dosya aktarimi, kumanda baglantisinin (WebSocket) uzerinden gider; internet gerekmez.
 *   gonderen:  {"t":"fbeg","id","name","size"}  sonra ikili 64 KB parcalar  sonra {"t":"fend"}
 *   alan:      her 16 parcada {"t":"fack"}, bitince {"t":"fdone","ok":true/false}
 * Bir baglantida ayni anda tek aktarim yapilir.
 */
object Xfer {
    const val CHUNK = 64 * 1024
    const val WINDOW = 16
}

data class XferState(
    val name: String = "",
    val done: Long = 0,
    val total: Long = 0,
    val dir: String = "",
    val msg: String = "",
)

data class TvFile(val name: String, val size: Long)

class AckGate {
    private val acks = Channel<Unit>(Channel.UNLIMITED)
    private val dones = Channel<Boolean>(Channel.UNLIMITED)

    fun ack() {
        acks.trySend(Unit)
    }

    fun done(ok: Boolean) {
        dones.trySend(ok)
    }

    fun reset() {
        while (acks.tryReceive().isSuccess) {
        }
        while (dones.tryReceive().isSuccess) {
        }
    }

    suspend fun awaitAck(ms: Long): Boolean = withTimeoutOrNull(ms) { acks.receive() } != null

    suspend fun awaitDone(ms: Long): Boolean = withTimeoutOrNull(ms) { dones.receive() } == true
}

suspend fun sendStream(
    input: InputStream,
    name: String,
    size: Long,
    sendText: (String) -> Unit,
    sendBin: (ByteArray) -> Unit,
    gate: AckGate,
    onProgress: (Long) -> Unit,
): Boolean = withContext(Dispatchers.IO) {
    gate.reset()
    val id = System.currentTimeMillis().toString()
    sendText(
        JSONObject().put("t", "fbeg").put("id", id).put("name", name).put("size", size).toString()
    )
    val buf = ByteArray(Xfer.CHUNK)
    var sent = 0L
    var n = 0
    input.use { ins ->
        while (true) {
            val r = ins.read(buf)
            if (r < 0) break
            if (r == 0) continue
            sendBin(buf.copyOf(r))
            sent += r
            n++
            onProgress(sent)
            if (n % Xfer.WINDOW == 0 && !gate.awaitAck(30_000)) return@withContext false
        }
    }
    sendText(JSONObject().put("t", "fend").put("id", id).toString())
    gate.awaitDone(60_000)
}

class FileReceiver(
    private val open: (String, Long) -> OutputStream?,
    private val sendText: (String) -> Unit,
    private val onProgress: (String, Long, Long) -> Unit = { _, _, _ -> },
    private val onDone: (String, Boolean) -> Unit = { _, _ -> },
) {
    private var out: OutputStream? = null
    private var name = ""
    private var size = 0L
    private var got = 0L
    private var chunks = 0
    private var id = ""

    fun begin(j: JSONObject) {
        closeQuiet()
        id = j.optString("id")
        name = j.optString("name")
        size = j.optLong("size", -1)
        got = 0
        chunks = 0
        out = try {
            open(name, size)
        } catch (e: Exception) {
            ZLog.e("dosya acilamadi", e)
            null
        }
    }

    fun data(b: ByteArray) {
        chunks++
        val o = out
        if (o != null) {
            try {
                o.write(b)
                got += b.size
            } catch (e: Exception) {
                ZLog.e("dosya yazilamadi", e)
                closeQuiet()
            }
        }
        if (chunks % Xfer.WINDOW == 0) {
            sendText(JSONObject().put("t", "fack").put("id", id).toString())
        }
        onProgress(name, got, size)
    }

    fun end() {
        val o = out
        var ok = false
        if (o != null) {
            try {
                o.flush()
                o.close()
                ok = size < 0 || got == size
            } catch (e: Exception) {
            }
        }
        out = null
        sendText(JSONObject().put("t", "fdone").put("id", id).put("ok", ok).toString())
        onDone(name, ok)
    }

    fun abort() = closeQuiet()

    private fun closeQuiet() {
        try {
            out?.close()
        } catch (e: Exception) {
        }
        out = null
    }
}

/** TV'ye gelen dosyalarin durdugu klasor (izin gerektirmez). */
object TvFiles {
    fun dir(c: Context): File {
        val d = c.getExternalFilesDir("Zapp") ?: File(c.filesDir, "Zapp")
        d.mkdirs()
        return d
    }

    fun safe(n: String): String =
        n.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[:*?\"<>|]"), "_")
            .ifBlank { "dosya" }

    fun file(c: Context, n: String): File? = File(dir(c), safe(n)).takeIf { it.isFile }

    fun open(c: Context, n: String): OutputStream = FileOutputStream(File(dir(c), safe(n)))

    fun list(c: Context): List<File> =
        dir(c).listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** Gelen dosyayi TV dosya yoneticisinde gorunsun diye Indirilenler/Zapp'a da kopyalar. */
    fun publish(c: Context, name: String): String {
        val f = file(c, name) ?: return ""
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val v = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Zapp")
                }
                val uri = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: return ""
                c.contentResolver.openOutputStream(uri)?.use { o -> f.inputStream().use { it.copyTo(o) } }
            } else {
                val d = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Zapp")
                d.mkdirs()
                f.copyTo(File(d, f.name), overwrite = true)
            }
            " (Download/Zapp klasörüne de kopyalandı)"
        } catch (e: Exception) {
            ZLog.e("genel klasore kopya", e)
            ""
        }
    }

    /** Download/Zapp icindeki kopyayi da siler (ayni adli). */
    fun removePublic(c: Context, name: String) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                c.contentResolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                    arrayOf(safe(name), "%Download/Zapp%"),
                )
            } else {
                File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Zapp"), safe(name)).delete()
            }
        } catch (e: Exception) {
            ZLog.e("genel kopya silinemedi", e)
        }
    }

    /** APK ise kurulum ekranini acar. Sonuc: kullaniciya gosterilecek mesaj. */
    fun install(c: Context, name: String): String {
        val f = file(c, name) ?: return "Dosya bulunamadı"
        if (Build.VERSION.SDK_INT >= 26 && !c.packageManager.canRequestPackageInstalls()) {
            try {
                c.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
            }
            return "TV'de Zapp için 'bu kaynaktan yüklemeye izin ver'i aç, sonra Kur'a tekrar bas"
        }
        return try {
            val uri = FileProvider.getUriForFile(c, "${c.packageName}.files", f)
            c.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
            "Kurulum ekranı TV'de açıldı"
        } catch (e: Exception) {
            ZLog.e("kurulum", e)
            "Kurulum açılamadı (bu TV'de paket yükleyici olmayabilir)"
        }
    }
}

/** Telefonda indirilenler klasoru (Indirilenler/Zapp). */
object PhoneFiles {
    fun open(c: Context, rawName: String): OutputStream? {
        val name = TvFiles.safe(rawName)
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Zapp")
            }
            val uri = c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
                ?: return null
            return c.contentResolver.openOutputStream(uri)
        }
        val d = File(c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Zapp")
        d.mkdirs()
        return FileOutputStream(File(d, name))
    }
}

fun fmtSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1048576.0)
    else -> "%.2f GB".format(b / 1073741824.0)
}
