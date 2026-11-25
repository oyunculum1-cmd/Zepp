package com.zapp.remote

import android.app.Application
import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/** Basit kayit tutucu. TV'de neyin ters gittigini (ornegin servis neden kapandi) gormek icin. */
object ZLog {
    private val mem = ArrayDeque<String>()
    private var file: File? = null
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    fun init(c: Context) {
        try {
            val f = File(c.filesDir, "zapp.log")
            if (f.exists() && f.length() > 200_000) f.delete()
            file = f
        } catch (e: Exception) {
        }
    }

    @Synchronized
    fun d(msg: String) {
        val line = fmt.format(Date()) + " " + msg
        mem.addLast(line)
        while (mem.size > 200) mem.removeFirst()
        try {
            file?.appendText(line + "\n")
        } catch (e: Exception) {
        }
    }

    fun e(msg: String, t: Throwable) = d("$msg: ${t.javaClass.simpleName} ${t.message}")

    @Synchronized
    fun tail(n: Int = 40): String {
        val lines: List<String> = if (mem.isNotEmpty()) {
            mem.toList()
        } else {
            try {
                file?.readLines() ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
        return lines.takeLast(n).joinToString("\n")
    }
}

/**
 * Uygulama sinifi. Arka plan thread'lerinde beklenmedik bir hata olursa surec oldurulmez:
 * erisilebilirlik servisi coker ise Android izni kendiliginden kapatir; bunu onlemek icin
 * hatayi kaydedip yutariz.
 */
class ZappApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ZLog.init(this)
        ZLog.d("surec basladi")
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            ZLog.e("HATA (${t.name})", e)
            if (t.name == "main") prev?.uncaughtException(t, e)
        }
    }
}
