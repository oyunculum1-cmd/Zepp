package com.zapp.remote

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** Hazir duvar kagitlari: ad, iki renk, parlama rengi. */
data class WallPreset(val name: String, val a: Int, val b: Int, val glow: Int)

val WALLS = listOf(
    WallPreset("Gece", 0xFF0B0F24.toInt(), 0xFF2A1B5E.toInt(), 0xFF7C5CFF.toInt()),
    WallPreset("Okyanus", 0xFF031B34.toInt(), 0xFF0E6BA8.toInt(), 0xFF22D3EE.toInt()),
    WallPreset("Gün batımı", 0xFF2B0B3F.toInt(), 0xFFD9534F.toInt(), 0xFFF59E0B.toInt()),
    WallPreset("Orman", 0xFF06150F.toInt(), 0xFF1F6B4A.toInt(), 0xFF34D399.toInt()),
    WallPreset("Gül", 0xFF2A0B22.toInt(), 0xFFB5307A.toInt(), 0xFFEC4899.toInt()),
    WallPreset("Buz", 0xFF0A1F2E.toInt(), 0xFF5FB4D6.toInt(), 0xFFE0F7FF.toInt()),
    WallPreset("Altın", 0xFF1A1205.toInt(), 0xFF8A5A0B.toInt(), 0xFFFFD166.toInt()),
    WallPreset("Grafit", 0xFF101114.toInt(), 0xFF3A3D46.toInt(), 0xFF9AA3C7.toInt()),
)

object Wall {
    private fun screen(ctx: Context): Pair<Int, Int> {
        val dm = ctx.resources.displayMetrics
        var w = dm.widthPixels.coerceAtLeast(1280)
        var h = dm.heightPixels.coerceAtLeast(720)
        if (w > 1920) {
            h = h * 1920 / w
            w = 1920
        }
        return w to h
    }

    fun presetBitmap(ctx: Context, n: Int): Bitmap {
        val p = WALLS[n.coerceIn(0, WALLS.lastIndex)]
        val (w, h) = screen(ctx)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, w.toFloat() * .6f, h.toFloat(), p.a, p.b, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        // yumusak parlamalar
        val blobs = listOf(Triple(.78f, .22f, .55f), Triple(.18f, .85f, .45f), Triple(.55f, .6f, .35f))
        blobs.forEach { (x, y, r) ->
            val rp = Paint(Paint.ANTI_ALIAS_FLAG)
            val rad = r * w
            rp.shader = RadialGradient(
                x * w, y * h, rad,
                intArrayOf((0x66 shl 24) or (p.glow and 0xFFFFFF), 0x00000000),
                null, Shader.TileMode.CLAMP,
            )
            c.drawCircle(x * w, y * h, rad, rp)
        }
        return bmp
    }

    fun fileBitmap(ctx: Context, f: File): Bitmap? {
        val (w, h) = screen(ctx)
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0) return null
        var s = 1
        while (o.outWidth / (s * 2) >= w && o.outHeight / (s * 2) >= h) s *= 2
        val src = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
            ?: return null
        // ekrani dolduracak sekilde ortadan kirp
        val k = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val sw = (w / k).toInt().coerceAtMost(src.width)
        val sh = (h / k).toInt().coerceAtMost(src.height)
        val crop = Bitmap.createBitmap(src, (src.width - sw) / 2, (src.height - sh) / 2, sw, sh)
        return Bitmap.createScaledBitmap(crop, w, h, true)
    }

    private fun apply(ctx: Context, bmp: Bitmap): String = try {
        val wm = WallpaperManager.getInstance(ctx)
        if (!wm.isWallpaperSupported) {
            "Bu TV sistem duvar kağıdını desteklemiyor. 'Tam ekran göster'i dene."
        } else {
            wm.setBitmap(bmp)
            "Duvar kağıdı ayarlandı. TV ana ekranı kendi duvar kağıdını kullanıyorsa değişmeyebilir; o zaman 'Tam ekran göster'i kullan."
        }
    } catch (e: Exception) {
        ZLog.e("duvar kagidi", e)
        "Duvar kağıdı ayarlanamadı (${e.javaClass.simpleName})"
    }

    fun preset(ctx: Context, n: Int): String = apply(ctx, presetBitmap(ctx, n))

    fun fromFile(ctx: Context, f: File): String {
        val b = fileBitmap(ctx, f) ?: return "Bu dosya bir resim değil"
        return apply(ctx, b)
    }

    fun clear(ctx: Context): String = try {
        WallpaperManager.getInstance(ctx).clear()
        "Duvar kağıdı sıfırlandı"
    } catch (e: Exception) {
        "Sıfırlanamadı"
    }

    /** Duvar kagidini tam ekran gosterir (TV ana ekrani sistem duvar kagidini kullanmasa da calisir). */
    fun view(ctx: Context, what: String) {
        try {
            ctx.startActivity(
                Intent(ctx, WallViewActivity::class.java)
                    .putExtra("what", what)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            ZLog.e("duvar goster", e)
        }
    }
}

class WallViewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val what = intent.getStringExtra("what") ?: "preset:0"
        val bmp = if (what.startsWith("preset:")) {
            Wall.presetBitmap(this, what.removePrefix("preset:").toIntOrNull() ?: 0)
        } else {
            TvFiles.file(this, what)?.let { Wall.fileBitmap(this, it) }
        }
        setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------- telefon sekmesi

@Composable
fun WallTab() {
    val ctx = LocalContext.current
    val notice by RemoteClient.notice.collectAsState()
    val x by RemoteClient.xfer.collectAsState()
    var last by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            RemoteClient.upload(ctx, listOf(uri)) { name ->
                last = name
                send("setwall", "name" to name)
            }
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Duvar kağıdı", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Hazır bir tane seç ya da telefondan fotoğraf gönder. TV ekranına uygulanır.",
            color = Muted,
            fontSize = 14.sp,
        )
        WALLS.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { col, w ->
                    val i = row * 2 + col
                    Box(
                        Modifier
                            .weight(1f)
                            .height(96.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(listOf(Color(w.a), Color(w.b), Color(w.glow).copy(alpha = .6f)))
                            )
                            .clickable {
                                last = "preset:$i"
                                send("wall", "n" to i)
                            },
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        Text(w.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
        ZButton("Telefondan fotoğraf seç ve gönder", Modifier.fillMaxWidth(), primary = true) {
            picker.launch(arrayOf("image/*"))
        }
        if (x.dir.isNotEmpty()) {
            Text("Gönderiliyor: ${x.name}  ${fmtSize(x.done)}", color = Ink, fontSize = 14.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ZButton("TV'de tam ekran göster", Modifier.weight(1f)) {
                send("wallview", "name" to last.ifEmpty { "preset:0" })
            }
            ZButton("Sıfırla", Modifier.weight(0.6f)) { send("wallclear") }
        }
        if (notice.isNotEmpty()) Text(notice, color = Cyan, fontSize = 14.sp)
        Text(
            "Not: Bazı TV'lerin ana ekranı kendi duvar kağıtlarını kullanır (TV ayarlarındaki 'Duvar kağıtları'). O durumda 'Tam ekran göster' her zaman çalışır.",
            color = Muted,
            fontSize = 13.sp,
        )
    }
}
