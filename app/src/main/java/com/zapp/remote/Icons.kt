package com.zapp.remote

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Uygulamanin tum simgeleri: emoji yok, hepsi cizgiyle cizilir. */
enum class Ic {
    UP, DOWN, LEFT, RIGHT, BACK, HOME, GEAR, WIFI, POWER, MOON, VOL_UP, VOL_DOWN, MUTE,
    PLAY, PLAYPAUSE, PREV, NEXT, REW, FF, REMOTE, TOUCH, MOUSE, KEYBOARD, APPS, FOLDER, IMAGE,
    GAMEPAD, PHONE, TV, SHIELD, LOCK, GLOBE, REFRESH, TRASH, SEND, DOWNLOAD, CHECK, CLOSE,
    LOG, USERS, KEY, PLUS, MINUS, BOLT, QR, SEARCH
}

@Composable
fun ZIcon(ic: Ic, size: Dp = 24.dp, tint: Color = Ink) {
    Canvas(Modifier.size(size)) { drawIc(ic, tint) }
}

/** Yazidan simge tahmini: butonlara otomatik simge koymak icin. */
fun inferIcon(text: String): Ic? {
    val t = text.lowercase()
    return when {
        t.isBlank() -> null
        t.startsWith("geri") -> Ic.BACK
        t.contains("ana ekran") -> Ic.HOME
        t.contains("ses kıs") -> Ic.VOL_DOWN
        t.contains("ses aç") -> Ic.VOL_UP
        t.contains("sessiz") -> Ic.MUTE
        t.contains("ayarlar") -> Ic.GEAR
        t.contains("wi") && t.contains("fi") -> Ic.WIFI
        t.contains("yeniden") || t.contains("sıfırla") && !t.contains("eşleş") -> Ic.REFRESH
        t.contains("kapat") && t.contains("tv") || t.contains("güç") || t.contains("uyandır") || t.contains("aç/kapa") -> Ic.POWER
        t.contains("uyku") || t.contains("uyut") -> Ic.MOON
        t.contains("sil") || t.contains("engelle") -> Ic.TRASH
        t.contains("gönder") || t.contains("yolla") -> Ic.SEND
        t.contains("indir") || t.contains("kaydet") -> Ic.DOWNLOAD
        t.contains("kur") -> Ic.DOWNLOAD
        t.contains("yukarı") -> Ic.UP
        t.contains("aşağı") -> Ic.DOWN
        t.contains("kayıt") || t.contains("hata") -> Ic.LOG
        t.contains("fare") -> Ic.MOUSE
        t.contains("yön tuş") -> Ic.REMOTE
        t.contains("kod") -> Ic.KEY
        t.contains("eşleş") -> Ic.USERS
        t.contains("bağlantıyı kes") -> Ic.CLOSE
        t.contains("bağlan") -> Ic.WIFI
        t.contains("tara") || t.contains("qr") -> Ic.QR
        t.contains("dosya") -> Ic.FOLDER
        t.contains("duvar") || t.contains("fotoğraf") || t.contains("resim") -> Ic.IMAGE
        t.contains("oyun") -> Ic.GAMEPAD
        t.contains("kilit") || t.contains("kapat") -> Ic.LOCK
        t.contains("aç") -> Ic.CHECK
        t.contains("seç") -> Ic.CHECK
        t.contains("tamam") || t.contains("evet") -> Ic.CHECK
        t.contains("vazgeç") || t.contains("iptal") -> Ic.CLOSE
        else -> null
    }
}

fun DrawScope.drawIc(ic: Ic, c: Color) {
    val s = size.minDimension / 24f
    val w = 2f * s
    val st = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    fun line(a: Float, b: Float, c2: Float, d: Float) = drawLine(c, p(a, b), p(c2, d), w, StrokeCap.Round)
    fun poly(fill: Boolean, close: Boolean, vararg v: Float) {
        val path = Path()
        path.moveTo(v[0] * s, v[1] * s)
        var i = 2
        while (i < v.size) {
            path.lineTo(v[i] * s, v[i + 1] * s)
            i += 2
        }
        if (close) path.close()
        drawPath(path, c, style = if (fill) Fill else st)
    }
    fun circ(x: Float, y: Float, r: Float, fill: Boolean = false) =
        drawCircle(c, r * s, p(x, y), style = if (fill) Fill else st)
    fun rr(x: Float, y: Float, wd: Float, ht: Float, r: Float, fill: Boolean = false) =
        drawRoundRect(c, p(x, y), Size(wd * s, ht * s), CornerRadius(r * s), style = if (fill) Fill else st)
    fun arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float) =
        drawArc(c, start, sweep, false, p(cx - r, cy - r), Size(2 * r * s, 2 * r * s), style = st)

    when (ic) {
        Ic.UP -> poly(true, true, 12f, 6f, 19f, 17f, 5f, 17f)
        Ic.DOWN -> poly(true, true, 12f, 18f, 5f, 7f, 19f, 7f)
        Ic.LEFT -> poly(true, true, 6f, 12f, 17f, 5f, 17f, 19f)
        Ic.RIGHT -> poly(true, true, 18f, 12f, 7f, 5f, 7f, 19f)
        Ic.BACK -> { line(19f, 12f, 5f, 12f); line(11f, 6f, 5f, 12f); line(11f, 18f, 5f, 12f) }
        Ic.HOME -> { poly(false, false, 3f, 11f, 12f, 3f, 21f, 11f); poly(false, false, 5f, 10f, 5f, 20f, 19f, 20f, 19f, 10f) }
        Ic.GEAR -> {
            circ(12f, 12f, 3f); circ(12f, 12f, 6.5f)
            for (i in 0 until 8) {
                val a = i * Math.PI.toFloat() / 4f
                line(12f + kotlin.math.cos(a) * 7.5f, 12f + kotlin.math.sin(a) * 7.5f, 12f + kotlin.math.cos(a) * 10f, 12f + kotlin.math.sin(a) * 10f)
            }
        }
        Ic.WIFI -> { arc(12f, 19f, 4f, -135f, 90f); arc(12f, 19f, 8f, -135f, 90f); arc(12f, 19f, 12f, -135f, 90f); circ(12f, 19f, 1.2f, true) }
        Ic.POWER -> { arc(12f, 13f, 8f, -60f, 300f); line(12f, 3f, 12f, 12f) }
        Ic.MOON -> { arc(12f, 12f, 8f, 60f, 240f); circ(18f, 6f, 1f, true); circ(20f, 10f, 0.8f, true) }
        Ic.VOL_UP -> { poly(false, true, 4f, 9f, 8f, 9f, 13f, 5f, 13f, 19f, 8f, 15f, 4f, 15f); arc(13f, 12f, 4f, -45f, 90f); arc(13f, 12f, 8f, -45f, 90f) }
        Ic.VOL_DOWN -> { poly(false, true, 4f, 9f, 8f, 9f, 13f, 5f, 13f, 19f, 8f, 15f, 4f, 15f); arc(13f, 12f, 4f, -45f, 90f) }
        Ic.MUTE -> { poly(false, true, 3f, 9f, 7f, 9f, 12f, 5f, 12f, 19f, 7f, 15f, 3f, 15f); line(16f, 9f, 21f, 15f); line(21f, 9f, 16f, 15f) }
        Ic.PLAY -> poly(true, true, 7f, 5f, 19f, 12f, 7f, 19f)
        Ic.PLAYPAUSE -> { poly(true, true, 3f, 6f, 11f, 12f, 3f, 18f); rr(14f, 6f, 2.5f, 12f, 0.5f, true); rr(18.5f, 6f, 2.5f, 12f, 0.5f, true) }
        Ic.PREV -> { rr(4f, 5f, 2.5f, 14f, 0.5f, true); poly(true, true, 20f, 5f, 9f, 12f, 20f, 19f) }
        Ic.NEXT -> { rr(17.5f, 5f, 2.5f, 14f, 0.5f, true); poly(true, true, 4f, 5f, 15f, 12f, 4f, 19f) }
        Ic.REW -> { poly(true, true, 11f, 6f, 3f, 12f, 11f, 18f); poly(true, true, 21f, 6f, 13f, 12f, 21f, 18f) }
        Ic.FF -> { poly(true, true, 3f, 6f, 11f, 12f, 3f, 18f); poly(true, true, 13f, 6f, 21f, 12f, 13f, 18f) }
        Ic.REMOTE -> { rr(7f, 2f, 10f, 20f, 3f); circ(12f, 8f, 1.6f, true); circ(9.5f, 14f, 1f, true); circ(14.5f, 14f, 1f, true); circ(12f, 18f, 1f, true) }
        Ic.TOUCH -> poly(false, true, 6f, 3f, 6f, 18f, 10f, 14f, 13f, 21f, 16f, 19.5f, 13f, 13f, 18f, 13f)
        Ic.MOUSE -> { rr(6f, 3f, 12f, 18f, 6f); line(12f, 3f, 12f, 10f); line(6f, 10f, 18f, 10f) }
        Ic.KEYBOARD -> {
            rr(2f, 6f, 20f, 12f, 2f)
            for (x in listOf(6f, 9.5f, 13f, 16.5f)) { circ(x, 10f, 0.7f, true); circ(x + 0.5f, 13f, 0.7f, true) }
            line(7f, 15.5f, 17f, 15.5f)
        }
        Ic.APPS -> { rr(4f, 4f, 6f, 6f, 1.5f); rr(14f, 4f, 6f, 6f, 1.5f); rr(4f, 14f, 6f, 6f, 1.5f); rr(14f, 14f, 6f, 6f, 1.5f) }
        Ic.FOLDER -> poly(false, true, 3f, 6f, 10f, 6f, 12f, 9f, 21f, 9f, 21f, 19f, 3f, 19f)
        Ic.IMAGE -> { rr(3f, 4f, 18f, 16f, 2f); circ(8.5f, 9.5f, 1.6f, true); poly(false, false, 3f, 18f, 9f, 12f, 13f, 16f, 16f, 13f, 21f, 18f) }
        Ic.GAMEPAD -> { rr(2f, 7f, 20f, 11f, 5f); line(7f, 10f, 7f, 15f); line(4.5f, 12.5f, 9.5f, 12.5f); circ(15.5f, 11.5f, 1.1f, true); circ(18f, 14f, 1.1f, true) }
        Ic.PHONE -> { rr(7f, 2f, 10f, 20f, 2.5f); line(11f, 19f, 13f, 19f) }
        Ic.TV -> { rr(2f, 4f, 20f, 13f, 2f); line(8f, 21f, 16f, 21f); line(12f, 17f, 12f, 21f) }
        Ic.SHIELD -> { poly(false, true, 12f, 2f, 20f, 5f, 20f, 12f, 12f, 22f, 4f, 12f, 4f, 5f); poly(false, false, 8.5f, 12f, 11f, 14.5f, 15.5f, 9.5f) }
        Ic.LOCK -> { rr(5f, 11f, 14f, 10f, 2f); arc(12f, 9f, 4f, 180f, 180f); line(8f, 9f, 8f, 11f); line(16f, 9f, 16f, 11f) }
        Ic.GLOBE -> { circ(12f, 12f, 9f); drawOval(c, p(8.5f, 3f), Size(7f * s, 18f * s), style = st); line(3f, 12f, 21f, 12f) }
        Ic.REFRESH -> { arc(12f, 12f, 8f, 20f, 300f); poly(false, false, 19.5f, 3f, 19.5f, 8.5f, 14f, 8.5f) }
        Ic.TRASH -> { line(4f, 6f, 20f, 6f); poly(false, false, 6f, 6f, 6f, 20f, 18f, 20f, 18f, 6f); line(10f, 10f, 10f, 16f); line(14f, 10f, 14f, 16f); line(9f, 3f, 15f, 3f) }
        Ic.SEND -> { poly(false, true, 3f, 11f, 21f, 3f, 14f, 21f, 11f, 13f); line(11f, 13f, 21f, 3f) }
        Ic.DOWNLOAD -> { line(12f, 3f, 12f, 15f); poly(false, false, 7f, 10f, 12f, 15f, 17f, 10f); line(4f, 20f, 20f, 20f) }
        Ic.CHECK -> poly(false, false, 4f, 12f, 10f, 18f, 20f, 6f)
        Ic.CLOSE -> { line(5f, 5f, 19f, 19f); line(19f, 5f, 5f, 19f) }
        Ic.LOG -> { line(8f, 6f, 20f, 6f); line(8f, 12f, 20f, 12f); line(8f, 18f, 20f, 18f); circ(4f, 6f, 0.8f, true); circ(4f, 12f, 0.8f, true); circ(4f, 18f, 0.8f, true) }
        Ic.USERS -> { circ(9f, 8f, 3f); arc(9f, 18f, 6f, 180f, 180f); circ(17f, 9f, 2.4f); arc(17f, 18f, 4f, 200f, 140f) }
        Ic.KEY -> { circ(8f, 12f, 4f); line(12f, 12f, 21f, 12f); line(18f, 12f, 18f, 15f); line(21f, 12f, 21f, 15f) }
        Ic.PLUS -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
        Ic.MINUS -> line(5f, 12f, 19f, 12f)
        Ic.BOLT -> poly(false, true, 13f, 2f, 5f, 13f, 11f, 13f, 10f, 22f, 19f, 10f, 13f, 10f)
        Ic.QR -> { rr(3f, 3f, 7f, 7f, 1f); rr(14f, 3f, 7f, 7f, 1f); rr(3f, 14f, 7f, 7f, 1f); circ(15f, 15f, 1f, true); circ(19f, 15f, 1f, true); circ(15f, 19f, 1f, true); circ(19f, 19f, 1f, true) }
        Ic.SEARCH -> { circ(10f, 10f, 6f); line(15f, 15f, 21f, 21f) }
    }
}
